package com.yiming.aiagentproject.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
public class RoutingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Double temperature = 0.0;

    private Integer maxTokens = 4096;

    private Integer maxRetries = 3;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private String responseFormat;

    @Bean(name = "routingChatModel")
    public ChatModel routingChatModel() {
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
}
