package com.yiming.aiagentproject.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.yiming.aiagentproject.ai.AiCodeGeneratorService;
import com.yiming.aiagentproject.ai.AiCodeGeneratorServiceFactory;
import com.yiming.aiagentproject.ai.imagecollection.HeuristicSlotPlanner;
import com.yiming.aiagentproject.ai.imagecollection.ImageCollectionOrchestrator;
import com.yiming.aiagentproject.ai.imagecollection.ImageSlotBinder;
import com.yiming.aiagentproject.ai.imagecollection.ImageSlotPromptBuilder;
import com.yiming.aiagentproject.ai.imagecollection.PlaceholderFallbackScrubber;
import com.yiming.aiagentproject.ai.imagecollection.VueProjectPathResolver;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlotPlan;
import com.yiming.aiagentproject.ai.model.HtmlCodeResult;
import com.yiming.aiagentproject.ai.model.MultiFileCodeResult;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.ai.model.message.AiResponseMessage;
import com.yiming.aiagentproject.ai.model.message.ToolExecutedMessage;
import com.yiming.aiagentproject.ai.model.message.ToolRequestMessage;
import com.yiming.aiagentproject.core.parser.CodeParserExecutor;
import com.yiming.aiagentproject.core.saver.CodeFileSaverExecutor;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * AI 代码生成门面类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private ImageCollectionOrchestrator imageCollectionOrchestrator;

    @Resource
    private HeuristicSlotPlanner heuristicSlotPlanner;

    /**
     * 等待异步素材收集结果的最长时间。
     * 超时则跳过"注入回合"，主链路代码已成功生成不会受影响。
     */
    private static final long IMAGE_INJECTION_WAIT_SECONDS = 25L;

    /**
     * Vue 工程目录中需要 scrub 的文本文件后缀。
     */
    private static final Set<String> VUE_SCRUB_EXTENSIONS = Set.of("vue", "js", "ts", "css", "html");

    /**
     * 占位符存在性匹配：用于"首轮是否已写出 __IMG_SLOT_*__"的快速判断，
     * 命中即认为模型遵守了槽位规则，可立即结束 SSE，不再阻塞等待异步素材。
     */
    private static final Pattern PLACEHOLDER_PRESENCE_PATTERN = Pattern.compile("__IMG_SLOT_[a-z0-9_]+__");

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        // 收集图片素材并增强提示词
        String enhancedMessage = imageCollectionOrchestrator.enhancePromptWithImages(userMessage);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(enhancedMessage);
                scrubHtmlResult(result);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(enhancedMessage);
                scrubMultiFileResult(result);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 统一入口：根据类型生成并保存代码（流式）
     * <p>
     * 关键改造：素材收集不再阻塞主链路，
     * 而是请求一进来就异步并发收集，主流程立即开始流式生成；
     * 首轮流式输出完成后，若素材已就绪则触发"素材注入回合"再开一次模型调用更新代码。
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);
        return switch (codeGenType) {
            case HTML -> buildSlotBasedCodeStream(
                    userMessage, CodeGenTypeEnum.HTML, appId,
                    aiCodeGeneratorService::generateHtmlCodeStream);
            case MULTI_FILE -> buildSlotBasedCodeStream(
                    userMessage, CodeGenTypeEnum.MULTI_FILE, appId,
                    aiCodeGeneratorService::generateMultiFileCodeStream);
            case VUE_PROJECT -> {
                // 阶段 1 暂不动 Vue：仍走老路径（后续阶段 3-5 接入槽位机制）。
                CompletableFuture<List<ImageResource>> imagesFuture =
                        imageCollectionOrchestrator.collectImagesAsync(userMessage);
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                Flux<String> firstStream = processTokenStream(tokenStream);
                yield processVueStreamWithInjection(
                        firstStream,
                        injectionPrompt -> processTokenStream(
                                aiCodeGeneratorService.generateVueProjectCodeStream(appId, injectionPrompt)),
                        imagesFuture,
                        appId);
            }

            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolCall(partialToolCall -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(partialToolCall);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * HTML / MULTI_FILE 共用的"启发式 plan + 槽位 prompt + 异步收集 + 注入回合"主链路：
     * <ol>
     *     <li>本地启发式产出 {@link ImageSlotPlan}（< 10ms 同步）；</li>
     *     <li>按 plan 异步派发素材收集（不阻塞主链路）；</li>
     *     <li>把槽位段落拼到 userMessage 后形成首轮 prompt，立即开始流式生成；</li>
     *     <li>首轮结束后若已命中占位符则跳过二轮，否则触发注入回合兜底。</li>
     * </ol>
     * 二轮回合实际多数场景不会触发——首轮模型按规则输出 {@code __IMG_SLOT_*__},
     * binder 直接命中替换,前端不会感知到"中途停顿 + 重新生成"。
     */
    private Flux<String> buildSlotBasedCodeStream(
            String userMessage,
            CodeGenTypeEnum codeGenType,
            Long appId,
            Function<String, Flux<String>> streamFn) {
        ImageSlotPlan slotPlan = heuristicSlotPlanner.buildHeuristicPlan(userMessage, codeGenType);
        int slotCount = slotPlan.getSlots() == null ? 0 : slotPlan.getSlots().size();
        CompletableFuture<List<ImageResource>> imagesFuture =
                imageCollectionOrchestrator.collectImagesByPlanAsync(slotPlan);
        String promptWithSlots = ImageSlotPromptBuilder.buildPromptWithImageSlots(userMessage, slotPlan);
        Flux<String> firstStream = streamFn.apply(promptWithSlots);
        return processCodeStreamWithInjection(
                firstStream, streamFn, codeGenType, appId, imagesFuture, slotCount);
    }

    /**
     * HTML / MULTI_FILE 的流式处理：
     * 1. 首轮流以原始 prompt 直接生成；
     * 2. 首轮完成后等待素材异步结果（短超时），若有素材则触发"注入回合"再开一次流式生成；
     * 3. 落盘以最后一轮（注入回合若未触发则为首轮）的代码为准，保证 parser 拿到的是最新版本。
     *
     * @param firstStream         首轮模型流
     * @param secondRoundFn       第二轮（注入回合）的流构造函数，入参为注入用户消息
     * @param codeGenTypeEnum     代码生成类型
     * @param appId               应用 ID
     * @param imagesFuture        异步收集素材的 Future
     * @param slotCount           槽位计划中的 slot 总数（用于打 bindRate 日志，0 表示无 plan）
     */
    private Flux<String> processCodeStreamWithInjection(
            Flux<String> firstStream,
            Function<String, Flux<String>> secondRoundFn,
            CodeGenTypeEnum codeGenTypeEnum,
            Long appId,
            CompletableFuture<List<ImageResource>> imagesFuture,
            int slotCount) {
        StringBuilder firstBuilder = new StringBuilder();
        StringBuilder secondBuilder = new StringBuilder();
        AtomicBoolean injectionRan = new AtomicBoolean(false);

        Flux<String> firstWithCollect = firstStream.doOnNext(firstBuilder::append);

        // 注入决策延迟到首轮流结束后（Flux.defer 在被订阅时才求值）：
        // - 首轮已写占位符 → 立即返回 empty，SSE 立刻结束，绝不阻塞 25s；
        //   binder 在 doOnComplete 用 imagesFuture.getNow(...) 拿真实素材，
        //   罕见的"素材尚未到位"场景由 whenComplete 后台兜底再写一次。
        // - 首轮没写占位符 → 走原路径：等异步素材，有就触发二轮 injection。
        Flux<String> injection = Flux.defer(() -> {
            if (PLACEHOLDER_PRESENCE_PATTERN.matcher(firstBuilder).find()) {
                log.info("首轮已写出占位符，SSE 立即结束，binder 走 doOnComplete 同步路径");
                return Flux.<String>empty();
            }
            log.info("首轮未写占位符，等待异步素材并决定是否走二轮 injection");
            // 用 boundedElastic 跑阻塞的 future.get，避免占用 Reactor 事件循环
            return Mono.fromCallable(() -> waitImages(imagesFuture))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(images -> {
                        if (CollUtil.isEmpty(images)) {
                            log.info("素材为空或等待超时，跳过素材注入回合");
                            return Flux.<String>empty();
                        }
                        injectionRan.set(true);
                        String injectionPrompt = imageCollectionOrchestrator.buildInjectionPrompt(images);
                        String banner = "\n\n[已收集到 " + images.size() + " 项素材，正在更新页面...]\n\n";
                        return Flux.concat(
                                Flux.just(banner),
                                secondRoundFn.apply(injectionPrompt)
                                        .doOnNext(secondBuilder::append)
                                        // 注入回合失败不应让整个请求失败：回退保留首轮代码
                                        .onErrorResume(e -> {
                                            log.warn("素材注入回合失败，回退到首轮代码: {}", e.getMessage());
                                            injectionRan.set(false);
                                            secondBuilder.setLength(0);
                                            return Flux.just("\n\n[素材注入失败，保留无素材版本]\n\n");
                                        })
                        );
                    });
        });

        return firstWithCollect
                .concatWith(injection)
                .doOnComplete(() -> {
                    log.info("流式 doOnComplete 触发: codeGenType={}, injectionRan={}, firstLen={}, secondLen={}",
                            codeGenTypeEnum, injectionRan.get(), firstBuilder.length(), secondBuilder.length());
                    String rawCode = pickFinalCode(firstBuilder, secondBuilder, injectionRan.get());
                    if (rawCode == null || rawCode.isEmpty()) {
                        log.warn("流式 doOnComplete: rawCode 为空，跳过保存");
                        return;
                    }
                    // getNow 不阻塞：素材未就绪时 binder 命中 0，scrubber 兜底为 picsum 占位
                    List<ImageResource> readyImages = imagesFuture.getNow(null);
                    boolean imagesPending = CollUtil.isEmpty(readyImages) && !imagesFuture.isDone();
                    bindAndSave(rawCode, readyImages, codeGenTypeEnum, appId, slotCount);
                    // 边界：首轮含占位符但素材还在收集 → 后台等结果再覆盖一次磁盘，
                    // 用户下次刷新预览即可看到真实图片（前端如果已经显示则保留兜底图）
                    if (!injectionRan.get() && imagesPending) {
                        log.info("素材未就绪，后台 whenComplete 后再补一次 bind+save");
                        imagesFuture.whenComplete((images, ex) -> {
                            if (ex != null) {
                                log.warn("后台素材等待异常: {}", ex.getMessage());
                                return;
                            }
                            if (CollUtil.isNotEmpty(images)) {
                                bindAndSave(rawCode, images, codeGenTypeEnum, appId, slotCount);
                            }
                        });
                    }
                })
                .doOnError(e -> log.error("流式生成异常: {}", e.getMessage(), e));
    }

    /**
     * 把首/二轮拼好的原始代码经 binder + scrubber 处理后落盘。
     * 抽出来是为了支持 doOnComplete 同步保存 + whenComplete 后台再保存两个调用点。
     */
    private void bindAndSave(
            String rawCode,
            List<ImageResource> images,
            CodeGenTypeEnum codeGenType,
            Long appId,
            int slotCount) {
        String code = rawCode;
        if (CollUtil.isNotEmpty(images)) {
            ImageSlotBinder.BindResult bindResult = ImageSlotBinder.bind(code, images);
            String bindRate = slotCount > 0
                    ? String.format("%.2f", (double) bindResult.hitCount() / slotCount)
                    : "n/a";
            log.info("代码保存前 bind: hitCount={}, slotCount={}, bindRate={}",
                    bindResult.hitCount(), slotCount, bindRate);
            code = bindResult.content();
        }
        PlaceholderFallbackScrubber.ScrubResult scrubResult = PlaceholderFallbackScrubber.scrub(code);
        log.info("代码保存前 scrub: placeholderLeakCount={}", scrubResult.placeholderLeakCount());
        code = scrubResult.content();
        try {
            Object parsedCode = CodeParserExecutor.executeParser(code, codeGenType);
            File savedDir = CodeFileSaverExecutor.executeSaver(parsedCode, codeGenType, appId);
            log.info("代码保存成功: {}", savedDir == null ? "?" : savedDir.getAbsolutePath());
        } catch (Exception e) {
            log.error("保存失败: {}", e.getMessage());
        }
    }

    /**
     * Vue 的流式处理：与 HTML/MULTI_FILE 同样逻辑，
     * 但不再二次解析保存（Vue 通过工具直接写文件，构建在 JsonMessageStreamHandler 末尾触发）。
     */
    private Flux<String> processVueStreamWithInjection(
            Flux<String> firstStream,
            Function<String, Flux<String>> secondRoundFn,
            CompletableFuture<List<ImageResource>> imagesFuture,
            Long appId) {
        Flux<String> injection = Mono.fromCallable(() -> waitImages(imagesFuture))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(images -> {
                    if (CollUtil.isEmpty(images)) {
                        log.info("Vue 项目：素材为空或等待超时，跳过素材注入回合");
                        return Flux.<String>empty();
                    }
                    String injectionPrompt = imageCollectionOrchestrator.buildInjectionPrompt(images);
                    // Vue 走 TokenStream + 工具调用，外层已包装成 JSON 消息被 JsonMessageStreamHandler 解析；
                    // 这里只把第二轮 TokenStream 接到首轮之后即可。注入失败不影响 Vue 项目的首轮成果（已落盘）。
                    return secondRoundFn.apply(injectionPrompt)
                            .onErrorResume(e -> {
                                log.warn("Vue 素材注入回合失败，跳过: {}", e.getMessage());
                                return Flux.<String>empty();
                            });
                });
        return firstStream.concatWith(injection)
                .doOnComplete(() -> scrubVueProjectSrc(appId));
    }

    /**
     * 占位符兜底安全网：递归扫描 Vue 工程的 src/，把残留的 __IMG_SLOT_*__ 替换为兜底图 URL。
     * 失败仅记录日志，不抛异常。
     */
    private void scrubVueProjectSrc(Long appId) {
        try {
            File srcDir = VueProjectPathResolver.resolveSrcDir(appId);
            if (srcDir == null || !srcDir.exists() || !srcDir.isDirectory()) {
                log.info("Vue 工程 src/ 不存在，跳过 scrub: appId={}", appId);
                return;
            }
            int hit = PlaceholderFallbackScrubber.scrubDirectory(srcDir, VUE_SCRUB_EXTENSIONS);
            log.info("Vue 工程 scrub 完成: appId={}, placeholderLeakCount={}", appId, hit);
        } catch (Exception e) {
            log.warn("Vue 工程 scrub 异常: appId={}, msg={}", appId, e.getMessage());
        }
    }

    /**
     * 同步路径：保存前把 HtmlCodeResult 字符串字段中的残留 __IMG_SLOT_*__ 兜底为可用 URL。
     */
    private void scrubHtmlResult(HtmlCodeResult result) {
        if (result == null) {
            return;
        }
        PlaceholderFallbackScrubber.ScrubResult scrubbed = PlaceholderFallbackScrubber.scrub(result.getHtmlCode());
        if (scrubbed.placeholderLeakCount() > 0) {
            result.setHtmlCode(scrubbed.content());
        }
        log.info("同步 HTML scrub: placeholderLeakCount={}", scrubbed.placeholderLeakCount());
    }

    /**
     * 同步路径：保存前把 MultiFileCodeResult 三块代码字段中的残留 __IMG_SLOT_*__ 兜底为可用 URL。
     */
    private void scrubMultiFileResult(MultiFileCodeResult result) {
        if (result == null) {
            return;
        }
        int total = 0;
        PlaceholderFallbackScrubber.ScrubResult htmlScrub = PlaceholderFallbackScrubber.scrub(result.getHtmlCode());
        if (htmlScrub.placeholderLeakCount() > 0) {
            result.setHtmlCode(htmlScrub.content());
        }
        total += htmlScrub.placeholderLeakCount();
        PlaceholderFallbackScrubber.ScrubResult cssScrub = PlaceholderFallbackScrubber.scrub(result.getCssCode());
        if (cssScrub.placeholderLeakCount() > 0) {
            result.setCssCode(cssScrub.content());
        }
        total += cssScrub.placeholderLeakCount();
        PlaceholderFallbackScrubber.ScrubResult jsScrub = PlaceholderFallbackScrubber.scrub(result.getJsCode());
        if (jsScrub.placeholderLeakCount() > 0) {
            result.setJsCode(jsScrub.content());
        }
        total += jsScrub.placeholderLeakCount();
        log.info("同步 MULTI_FILE scrub: placeholderLeakCount={}", total);
    }

    private List<ImageResource> waitImages(CompletableFuture<List<ImageResource>> imagesFuture) {
        try {
            return imagesFuture.get(IMAGE_INJECTION_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("等待素材收集超时（{}s），跳过注入回合", IMAGE_INJECTION_WAIT_SECONDS);
            return null;
        } catch (Exception e) {
            log.warn("等待素材收集异常: {}", e.getMessage());
            return null;
        }
    }

    private String pickFinalCode(StringBuilder firstBuilder, StringBuilder secondBuilder, boolean injectionRan) {
        if (injectionRan && secondBuilder.length() > 0) {
            return secondBuilder.toString();
        }
        return firstBuilder.toString();
    }


}
