package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.StopWatch;
import com.yiming.aiagentproject.langgraph4j.ai.ImageCollectionPlanService;
import com.yiming.aiagentproject.langgraph4j.model.ImageCollectionPlan;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.langgraph4j.tool.ImageSearchTool;
import com.yiming.aiagentproject.langgraph4j.tool.LogoGeneratorTool;
import com.yiming.aiagentproject.langgraph4j.tool.MermaidDiagramTool;
import com.yiming.aiagentproject.langgraph4j.tool.UndrawIllustrationTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 基于原始用户提示词，自动收集相关素材并产出增强后的提示词。
     * 任意阶段失败都会降级为返回原提示词，保证主流程不受影响。
     */
    public String enhancePromptWithImages(String originalPrompt) {
        List<ImageResource> images = collectImages(originalPrompt);
        return buildEnhancedPrompt(originalPrompt, images);
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
            builder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
            for (ImageResource image : imageList) {
                builder.append("- ")
                        .append(image.getCategory().getText())
                        .append("：")
                        .append(image.getDescription())
                        .append("（")
                        .append(image.getUrl())
                        .append("）\n");
            }
        }
        String enhanced = builder.toString();
        log.info("提示词增强完成，增强后长度: {} 字符", enhanced.length());
        return enhanced;
    }
}
