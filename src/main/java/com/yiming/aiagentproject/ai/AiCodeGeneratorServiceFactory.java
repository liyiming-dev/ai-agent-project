package com.yiming.aiagentproject.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yiming.aiagentproject.ai.memory.ThinkingTrimmingChatMemory;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.ai.tools.ToolManager;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import com.yiming.aiagentproject.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private ToolManager toolManager;

    /**
     * Vue 项目对话记忆使用的 token 预算。
     * 配合 {@link ThinkingTrimmingChatMemory} 剥离历史 reasoning_content 后，
     * 上限以"实际可见文本 + 工具调用 + 工具结果"计算，避免按消息条数计窗时单条 thinking 可能超几 k token。
     *
     * <p>2026-05-14 从 12000 收紧到 8000：Vue 单轮会写 20+ 文件，
     * 每个 tool result 都会进 memory，越往后 prompt 越大，DeepSeek 服务端 prefill 时间线性上升，
     * 表现为"越输出越慢/越卡"。8000 token 在保留当前轮工具链 + 1-2 轮历史摘要之间取平衡，
     * 多轮迭代场景下 AI 会逐渐丢失更早的轮次记忆 — 对"完整重写"的 Vue 项目流影响有限。
     */
    private static final int VUE_MEMORY_MAX_TOKENS = 24000;

    /**
     * 用 OpenAI 的 cl100k_base 编码近似 DeepSeek 的分词。
     * DeepSeek 没有公开 SDK 提供的 tokenizer，cl100k 误差通常在 ±10% 内，对窗口控制足够用。
     */
    private final TokenCountEstimator chatMemoryTokenCountEstimator =
            new OpenAiTokenCountEstimator("gpt-4");

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId,codeGenType);
        //如果缓存里有对应的appId,就从缓存中取出服务,如果没有(第一次创建对话)就调用createAi...方法创建新的ai服务实例
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return serviceCache.get(String.valueOf(appId), key -> createAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML));
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        return createAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        // Vue 项目使用 token 窗口 + thinking 裁剪：
        //   1. token 窗口：让窗口按真实 token 预算裁剪，避免 maxMessages=20 时单条 thinking 几 k token 撑爆 prompt
        //   2. thinking 裁剪：仅最近一条 AiMessage 的 reasoning_content 回传给模型，历史轮 thinking 全部剥掉，
        //      解决"越输出越慢"——历史 reasoning 累积造成的 prompt 雪球
        // HTML / MULTI_FILE 不走 reasoning，沿用消息窗口即可
        ChatMemory chatMemory;
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            ChatMemory inner = TokenWindowChatMemory.builder()
                    .id(appId)
                    .chatMemoryStore(redisChatMemoryStore)
                    .maxTokens(VUE_MEMORY_MAX_TOKENS, chatMemoryTokenCountEstimator)
                    .build();
            chatMemory = new ThinkingTrimmingChatMemory(inner);
        } else {
            chatMemory = MessageWindowChatMemory
                    .builder()
                    .id(appId)
                    .chatMemoryStore(redisChatMemoryStore)
                    .maxMessages(20)
                    .build();
        }
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目生成使用推理模型
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(toolManager.getAllTools())
                    // 安全校验在入口对用户原始输入执行，避免误判已增强的提示词
//                  .outputGuardrails(new RetryOutputGuardrail()) 开启后将无法流式输出
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    // Vue 项目正常一次会写 20+ 文件，再叠加修改场景的读改链，30 偏紧；放宽到 60
                    // 真正的循环防护靠 FileModifyTool 的重复失配拦截 + 提示词分批输出进度（中间文本会重置计数）
                    .maxSequentialToolsInvocations(60)
                    .build();
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(streamingChatModel)
//                  .outputGuardrails(new RetryOutputGuardrail()) 开启后将无法流式输出
                    .chatMemory(chatMemory)
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + ":" + codeGenType.getValue();
    }

    /**
     * 把同一 appId 在缓存里的所有 AiCodeGeneratorService 实例剔除。
     * 用在"新对话"按钮:刷新 currentSessionId 后,旧的 AiServices 持有的 ChatMemory 还指向旧 session 装载结果,
     * 必须强制重建才会重新走 loadChatHistoryToMemory(按新 sessionId 过滤,空历史)。
     *
     * 兼容两种 key 形态:
     *  - 新 key: "{appId}:{codeGenType}"
     *  - 老 key: "{appId}"(早期重载 getAiCodeGeneratorService(appId) 用的)
     */
    public void evictByAppId(long appId) {
        String prefix = appId + ":";
        String legacyKey = String.valueOf(appId);
        serviceCache.asMap().keySet().removeIf(key -> key.equals(legacyKey) || key.startsWith(prefix));
        log.info("已清理 appId={} 的 AiCodeGeneratorService 缓存,下次调用将重建 ChatMemory", appId);
    }

}

