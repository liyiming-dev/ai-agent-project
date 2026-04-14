package com.yiming.aiagentproject.ai;

import com.yiming.aiagentproject.ai.model.HtmlCodeResult;
import com.yiming.aiagentproject.ai.model.MultiFileCodeResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCodeGeneratorServiceTest {

    private ChatModel chatModel;

    private AiCodeGeneratorService aiCodeGeneratorService;

    @BeforeEach
    void setUp() {
        mockModelResponse("default mock response");
    }

    @Test
    void generateHtmlCode() {
        mockModelResponse("""
                {
                  "htmlCode": "<html><body>login page</body></html>",
                  "description": "一个简单的登录页面"
                }
                """);

        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("一个简单的登录页面,不超过20行");

        Assertions.assertNotNull(result);

    }

    @Test
    void generateMultiFileCode() {
        mockModelResponse("""
                {
                  "htmlCode": "<html><body>login page</body></html>",
                  "cssCode": "body { margin: 0; }",
                  "jsCode": "console.log('login');",
                  "description": "一个简单的登录页面"
                }
                """);

        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("一个简单的登录页面,不超过50行");

        Assertions.assertNotNull(result);

    }

    private void mockModelResponse(String content) {
        chatModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(content))
                        .build();
            }
        };
        aiCodeGeneratorService = AiServices.create(AiCodeGeneratorService.class, chatModel);
    }
}
