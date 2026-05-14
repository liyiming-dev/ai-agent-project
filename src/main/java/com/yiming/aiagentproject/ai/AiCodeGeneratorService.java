package com.yiming.aiagentproject.ai;

import com.yiming.aiagentproject.ai.model.HtmlCodeResult;
import com.yiming.aiagentproject.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AiCodeGeneratorService {

    /**
     * 生成html代码
     * @param userMessage
     * @return
     */
    @UserMessage(fromResource = "prompt/generate-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文本代码
     * @param userMessage
     * @return
     */
    @UserMessage(fromResource = "prompt/generate-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码（流式）。返回 {@link TokenStream} 以便在 onCompleteResponse 中
     * 拿到 finishReason / tokenUsage，用于排查 length 截断；外层 facade 通过适配器
     * 转换成 {@code Flux<String>}。
     *
     * @param userMessage 用户消息
     * @return TokenStream
     */
    @SystemMessage(fromResource = "prompt/generate-html-system-prompt.txt")
    TokenStream generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码（流式）。同 generateHtmlCodeStream，返回 TokenStream 暴露完成元数据。
     *
     * @param userMessage 用户消息
     * @return TokenStream
     */
    @SystemMessage(fromResource = "prompt/generate-multi-file-system-prompt.txt")
    TokenStream generateMultiFileCodeStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/generate-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

}
