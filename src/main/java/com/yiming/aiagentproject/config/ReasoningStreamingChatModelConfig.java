package com.yiming.aiagentproject.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens = 32768;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     * 开启 returnThinking / sendThinking：
     *  - returnThinking=true  解析响应中的 reasoning_content 并保存到 AiMessage
     *  - sendThinking=true    多轮对话时把历史 AiMessage 的 reasoning_content 回传
     *    修复 "The `reasoning_content` in the thinking mode must be passed back to the API." 报错
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }
}
