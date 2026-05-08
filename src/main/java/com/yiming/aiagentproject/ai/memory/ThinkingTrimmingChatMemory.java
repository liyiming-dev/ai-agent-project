package com.yiming.aiagentproject.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 装饰器：在每次读取消息列表时，剥掉"上一轮用户消息之前"所有 AiMessage 的 reasoning_content。
 *
 * 解决 Vue 项目使用 reasoning 模型 + sendThinking=true 时，
 * 历史轮 thinking 段累积导致 prompt 雪球、"越输出越慢" 的问题。
 *
 * 边界：DeepSeek thinking 模式要求"当前活跃工具调用链"中每条带 tool_calls 的 AiMessage 都必须回传
 * reasoning_content，否则 API 直接拒绝（invalid_request_error: "The `reasoning_content` in the
 * thinking mode must be passed back to the API."）。因此本装饰器只裁剪最近一条 UserMessage 之前的
 * 已完成历史轮，当前轮工具链整链保留。
 *
 * - 存储不变：底层 ChatMemory 仍然完整保留 thinking，便于排错和后续查询。
 * - 仅在出口处裁剪：messages() 被 AiServices 调用以拼装请求时才剥离。
 */
public class ThinkingTrimmingChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    public ThinkingTrimmingChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> original = delegate.messages();
        if (original == null || original.isEmpty()) {
            return original;
        }
        // 找到最近一条 UserMessage 的索引：它之后的所有 AiMessage 属于"当前活跃工具调用链"，thinking 必须保留
        int lastUserIndex = -1;
        for (int i = original.size() - 1; i >= 0; i--) {
            if (original.get(i) instanceof UserMessage) {
                lastUserIndex = i;
                break;
            }
        }
        List<ChatMessage> result = new ArrayList<>(original.size());
        for (int i = 0; i < original.size(); i++) {
            ChatMessage m = original.get(i);
            if (m instanceof AiMessage ai && i < lastUserIndex && ai.thinking() != null) {
                result.add(stripThinking(ai));
            } else {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private AiMessage stripThinking(AiMessage ai) {
        return AiMessage.builder()
                .text(ai.text())
                .toolExecutionRequests(ai.toolExecutionRequests())
                .attributes(ai.attributes())
                .build();
    }
}
