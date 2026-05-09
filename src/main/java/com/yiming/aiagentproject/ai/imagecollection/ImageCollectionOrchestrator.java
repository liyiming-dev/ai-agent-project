package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlot;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlotPlan;
import com.yiming.aiagentproject.langgraph4j.ai.ImageCollectionPlanService;
import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageCollectionPlan;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.langgraph4j.tool.ImageSearchTool;
import com.yiming.aiagentproject.langgraph4j.tool.LogoGeneratorTool;
import com.yiming.aiagentproject.langgraph4j.tool.MermaidDiagramTool;
import com.yiming.aiagentproject.langgraph4j.tool.UndrawIllustrationTool;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 图片收集编排器：在原有架构（非 langgraph4j 工作流）下，
 * 串联「图片收集计划 → 并发收集图片 → 提示词增强」整套能力。
 */
@Slf4j
@Component
public class ImageCollectionOrchestrator {

    @Resource
    private ImageCollectionPlanService imageCollectionPlanService;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /**
     * 专门用于"图片收集外层调度"的线程池：
     * - 与内部并发任务的 ForkJoinPool common 池解耦，避免被业务线程拖死
     * - daemon 线程，应用退出时不阻塞 JVM
     */
    private final ExecutorService outerExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "image-collect-outer-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    @PreDestroy
    public void shutdown() {
        outerExecutor.shutdownNow();
    }

    /**
     * 基于原始用户提示词，自动收集相关素材并产出增强后的提示词。
     * 任意阶段失败都会降级为返回原提示词，保证主流程不受影响。
     */
    public String enhancePromptWithImages(String originalPrompt) {
        List<ImageResource> images = collectImages(originalPrompt);
        return buildEnhancedPrompt(originalPrompt, images);
    }

    /**
     * 异步收集素材：立即返回 CompletableFuture，主链路无需等待。
     * 失败时返回空列表，调用方据此决定是否触发"注入回合"。
     */
    public CompletableFuture<List<ImageResource>> collectImagesAsync(String originalPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("异步图片收集失败: {}", e.getMessage(), e);
                return new ArrayList<>();
            }
        }, outerExecutor);
    }

    /**
     * 按 ImageSlotPlan 异步收集素材：每个 slot 派生一个收集任务，
     * 返回的每条 {@link ImageResource} 都会回填对应 {@code slotId}，
     * 与首轮 prompt 中的 {@code __IMG_SLOT_*__} 占位符一一对应。
     *
     * <p>失败时返回空列表，调用方据此走兜底替换。
     */
    public CompletableFuture<List<ImageResource>> collectImagesByPlanAsync(ImageSlotPlan plan) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return collectImagesByPlan(plan);
            } catch (Exception e) {
                log.error("按 plan 异步图片收集失败: {}", e.getMessage(), e);
                return new ArrayList<>();
            }
        }, outerExecutor);
    }

    /**
     * 同步执行：按 ImageSlotPlan 派发收集任务并回填 slotId。
     */
    public List<ImageResource> collectImagesByPlan(ImageSlotPlan plan) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<ImageResource> collected = new ArrayList<>();
        if (plan == null || CollUtil.isEmpty(plan.getSlots())) {
            return collected;
        }
        try {
            List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
            for (ImageSlot slot : plan.getSlots()) {
                CompletableFuture<List<ImageResource>> future = dispatchSlot(slot);
                if (future != null) {
                    futures.add(future);
                }
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<List<ImageResource>> future : futures) {
                List<ImageResource> images = future.get();
                if (CollUtil.isNotEmpty(images)) {
                    collected.addAll(images);
                }
            }
            log.info("按 plan 收集完成，slot 数={}，素材数={}", plan.getSlots().size(), collected.size());
        } catch (Exception e) {
            log.error("按 plan 图片收集失败: {}", e.getMessage(), e);
        }
        stopWatch.stop();
        log.info("按 plan 图片收集总耗时: {} ms", stopWatch.getTotalTimeMillis());
        return collected;
    }

    /**
     * 单个 slot 派发到对应工具：每条返回的 {@link ImageResource} 回填 slotId，
     * 收集失败仅记录日志并返回空列表（不抛异常，避免影响其它 slot）。
     *
     * <p>注意：子任务使用 ForkJoinPool.commonPool（{@code CompletableFuture.supplyAsync} 默认池），
     * 与 {@link #outerExecutor} 错开池别，避免外层任务占满 outerExecutor 后再 join 子任务造成死锁
     * （历史上 {@code collectImages} 的子任务亦走默认池）。
     */
    private CompletableFuture<List<ImageResource>> dispatchSlot(ImageSlot slot) {
        if (slot == null || StrUtil.isEmpty(slot.getSlotId()) || slot.getCategory() == null) {
            return null;
        }
        String slotId = slot.getSlotId();
        String query = StrUtil.blankToDefault(slot.getQuery(), slot.getAlt());
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ImageResource> raw = switch (slot.getCategory()) {
                    case CONTENT -> imageSearchTool.searchContentImages(query);
                    case ILLUSTRATION -> undrawIllustrationTool.searchIllustrations(query);
                    case ARCHITECTURE -> mermaidDiagramTool.generateMermaidDiagram(query, query);
                    case LOGO -> logoGeneratorTool.generateLogos(query);
                };
                return attachSlotId(raw, slotId);
            } catch (Exception e) {
                log.warn("slot[{}] 收集失败: {}", slotId, e.getMessage());
                return new ArrayList<>();
            }
        });
    }

    /**
     * 把 slotId 回填到工具返回的每条 ImageResource 上。
     * 旧工具签名保持不变，slotId 在编排层统一回填，最小化改动。
     */
    private static List<ImageResource> attachSlotId(List<ImageResource> images, String slotId) {
        if (CollUtil.isEmpty(images)) {
            return new ArrayList<>();
        }
        for (ImageResource image : images) {
            if (image != null && StrUtil.isEmpty(image.getSlotId())) {
                image.setSlotId(slotId);
            }
        }
        return images;
    }

    /**
     * 仅执行收集阶段：制定计划 → 并发收集 → 汇总。
     */
    public List<ImageResource> collectImages(String originalPrompt) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<ImageResource> collectedImages = new ArrayList<>();
        try {
            ImageCollectionPlan plan = imageCollectionPlanService.planImageCollection(originalPrompt);
            log.info("获取到图片收集计划，开始并发执行");

            List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
            if (plan.getContentImageTasks() != null) {
                for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            imageSearchTool.searchContentImages(task.query())));
                }
            }
            if (plan.getIllustrationTasks() != null) {
                for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            undrawIllustrationTool.searchIllustrations(task.query())));
                }
            }
            if (plan.getDiagramTasks() != null) {
                for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            mermaidDiagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                }
            }
            if (plan.getLogoTasks() != null) {
                for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            logoGeneratorTool.generateLogos(task.description())));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<List<ImageResource>> future : futures) {
                List<ImageResource> images = future.get();
                if (images != null) {
                    collectedImages.addAll(images);
                }
            }
            log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
        } catch (Exception e) {
            log.error("图片收集失败: {}", e.getMessage(), e);
        }
        stopWatch.stop();
        log.info("图片收集总耗时: {} ms", stopWatch.getTotalTimeMillis());
        return collectedImages;
    }

    /**
     * 将收集到的图片资源拼接到原始提示词后，形成增强后的提示词。
     */
    public String buildEnhancedPrompt(String originalPrompt, List<ImageResource> imageList) {
        StringBuilder builder = new StringBuilder();
        builder.append(originalPrompt);
        if (CollUtil.isNotEmpty(imageList)) {
            builder.append("\n\n## 可用素材资源\n");
            builder.append("以下素材按类别分组，请严格按各分组的使用规则嵌入；不符合规则的素材不要使用。\n");
            appendImagesByCategory(builder, imageList);
        }
        String enhanced = builder.toString();
        log.info("提示词增强完成，增强后长度: {} 字符", enhanced.length());
        return enhanced;
    }

    /**
     * 构造"素材注入回合"使用的用户消息：基于上一轮已生成的代码，
     * 让模型按使用规则把素材补进去并重新输出完整代码。
     */
    public String buildInjectionPrompt(List<ImageResource> imageList) {
        StringBuilder builder = new StringBuilder();
        builder.append("素材已收集完毕。请基于你刚才生成的代码，将以下素材按各分组的使用规则嵌入到合适的位置，"
                + "保持原有的结构与功能不变，重新输出完整的代码（保留与上一轮相同的代码块格式）。"
                + "如果某条素材没有合适位置，宁可丢弃也不要硬塞。\n\n");
        builder.append("## 可用素材资源\n");
        appendImagesByCategory(builder, imageList);
        return builder.toString();
    }

    private static void appendImagesByCategory(StringBuilder builder, List<ImageResource> imageList) {
        Map<ImageCategoryEnum, List<ImageResource>> grouped = imageList.stream()
                .filter(img -> img.getCategory() != null)
                .collect(Collectors.groupingBy(ImageResource::getCategory));
        for (ImageCategoryEnum category : ImageCategoryEnum.values()) {
            List<ImageResource> images = grouped.get(category);
            if (CollUtil.isEmpty(images)) {
                continue;
            }
            builder.append("\n### ").append(category.getText()).append("\n");
            builder.append(usageRuleOf(category)).append("\n");
            for (ImageResource image : images) {
                builder.append("- ")
                        .append(image.getDescription())
                        .append("（")
                        .append(image.getUrl())
                        .append("）\n");
            }
        }
    }

    private static String usageRuleOf(ImageCategoryEnum category) {
        return switch (category) {
            case CONTENT -> "用途：网站正文配图，可在卡片、列表、详情等内容区域使用。";
            case ILLUSTRATION -> "用途：装饰性插画，用于空状态、特性介绍等装饰位置。";
            case LOGO -> "用途：品牌标识，仅用于导航栏 / 页脚 / 关于页等品牌展示位。";
            case ARCHITECTURE -> "用途：仅当页面存在专门的「技术架构 / 工作原理 / 业务流程」等说明性区块时使用；"
                    + "禁止放入首页 Hero、Banner、导航栏、通用卡片或纯装饰位；若全站没有合适位置，请直接忽略本组素材。";
        };
    }
}
