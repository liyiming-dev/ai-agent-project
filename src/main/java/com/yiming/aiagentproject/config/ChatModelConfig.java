package com.yiming.aiagentproject.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.4}")
    private Double temperature;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:8192}")
    private Integer maxTokens;

    @Value("${langchain4j.open-ai.chat-model.log-requests:false}")
    private Boolean logRequests;

    @Value("${langchain4j.open-ai.chat-model.log-responses:false}")
    private Boolean logResponses;

    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:${langchain4j.open-ai.chat-model.model-name}}")
    private String streamingModelName;

    // streaming-chat-model.max-tokens 必须独立读取：HTML/多文件流式输出需要远大于非流式（routing/图片计划）的预算
    @Value("${langchain4j.open-ai.streaming-chat-model.max-tokens:${langchain4j.open-ai.chat-model.max-tokens:8192}}")
    private Integer streamingMaxTokens;

    @Value("${langchain4j.open-ai.streaming-chat-model.log-requests:${langchain4j.open-ai.chat-model.log-requests:false}}")
    private Boolean streamingLogRequests;

    @Value("${langchain4j.open-ai.streaming-chat-model.log-responses:${langchain4j.open-ai.chat-model.log-responses:false}}")
    private Boolean streamingLogResponses;

    @Value("${langchain4j.open-ai.chat-model.response-format:}")
    private String responseFormat;

    @Value("${langchain4j.open-ai.chat-model.max-retries:3}")
    private Integer maxRetries;

    @Bean
    public ChatModel chatModel() {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(logResponses);
        if (responseFormat != null && !responseFormat.isBlank()) {
            builder.responseFormat(responseFormat);
        }
        return builder.build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(streamingModelName)
                .maxTokens(streamingMaxTokens)
                .logRequests(streamingLogRequests)
                .logResponses(streamingLogResponses)
                .build();
    }
}
