package com.yiming.aiagentproject.ai.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 装饰器：在每次读取消息列表时，剥掉"上一轮用户消息之前"所有 AiMessage 的 reasoning_content，
 * 同时把 tool_calls / tool_result 的孤儿对统一清理掉。
 *
 * 解决两个问题：
 * 1. Vue 项目使用 reasoning 模型 + sendThinking=true 时，历史轮 thinking 段累积导致 prompt 雪球、
 *    "越输出越慢"。
 * 2. {@code TokenWindowChatMemory} 在窗口偏紧时偶发把 AiMessage(tool_calls) 砍掉却留下后面的
 *    ToolExecutionResultMessage，触发 DeepSeek/OpenAI:
 *    {@code Messages with role 'tool' must be a response to a preceding message with 'tool_calls'}。
 *    本装饰器在出口处用 open tool_call_ids 集合做配对校验，丢掉任何无法配对的 tool 结果，
 *    并把 tool_calls 中没有结果回写的请求 id 从 AiMessage 上摘掉，保证发往模型的消息序列恒合法。
 *
 * 边界：DeepSeek thinking 模式要求"当前活跃工具调用链"中每条带 tool_calls 的 AiMessage 都必须回传
 * reasoning_content，否则 API 直接拒绝（invalid_request_error: "The `reasoning_content` in the
 * thinking mode must be passed back to the API."）。因此本装饰器只裁剪最近一条 UserMessage 之前的
 * 已完成历史轮，当前轮工具链整链保留。
 *
 * - 存储不变：底层 ChatMemory 仍然完整保留 thinking 与 tool 配对，便于排错和后续查询。
 * - 仅在出口处裁剪：messages() 被 AiServices 调用以拼装请求时才剥离。
 */
@Slf4j
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
        // 单趟扫描完成 thinking 剥离 + tool 配对修复：
        // - openToolCallIds 记录"已发起但还未被结果回写"的 tool_call_id 集合
        // - 遇到 ToolExecutionResultMessage：id 在集合中→保留并出栈；否则视为孤儿丢弃（这就是 DeepSeek 报
        //   "Messages with role 'tool' must be a response to a preceding message with 'tool_calls'" 的根因）
        // - 遇到 AiMessage：先收集当前 message 的 tool_calls 进集合，再按需 strip thinking。
        //   注意：在 AiMessage 进入时不清空旧的 openToolCallIds — 同一条 AiMessage 可能在 tool_calls
        //   之间夹带文本，与下一条 AiMessage 之间还有 ToolExecutionResultMessage 衔接。
        // - 遇到 UserMessage：清空集合（新轮次，旧 tool_calls 即便没被回写也无法补救，留在 AiMessage 上
        //   会导致 API 拒绝，所以走 second pass 把孤儿 tool_calls 从 AiMessage 摘掉）
        List<ChatMessage> result = new ArrayList<>(original.size());
        Set<String> openToolCallIds = new HashSet<>();
        // 记录"被消费过的 tool_call_id"，用于第二轮过滤 AiMessage 上未被回写的 tool_calls
        Set<String> answeredToolCallIds = new HashSet<>();
        for (int i = 0; i < original.size(); i++) {
            ChatMessage m = original.get(i);
            if (m instanceof ToolExecutionResultMessage trm) {
                String id = trm.id();
                if (id != null && openToolCallIds.remove(id)) {
                    answeredToolCallIds.add(id);
                    result.add(trm);
                } else {
                    log.warn("丢弃孤儿 ToolExecutionResultMessage: id={}, toolName={}", id, trm.toolName());
                }
            } else if (m instanceof AiMessage ai) {
                if (ai.toolExecutionRequests() != null) {
                    for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                        if (req.id() != null) {
                            openToolCallIds.add(req.id());
                        }
                    }
                }
                // 历史轮 thinking 剥离 + tool_calls 兜底过滤都放到第二轮一次性处理（需要 answeredToolCallIds 全集）
                result.add(ai);
            } else {
                if (m instanceof UserMessage && !openToolCallIds.isEmpty()) {
                    log.warn("UserMessage 之前还有 {} 个未回写结果的 tool_calls，将从前一条 AiMessage 摘除", openToolCallIds.size());
                }
                openToolCallIds.clear();
                result.add(m);
            }
        }
        // 第二轮：strip thinking + 剔除 AiMessage 上未被结果回写的 tool_calls
        for (int i = 0; i < result.size(); i++) {
            ChatMessage m = result.get(i);
            if (!(m instanceof AiMessage ai)) {
                continue;
            }
            boolean needsStripThinking = i < lastUserIndex && ai.thinking() != null;
            List<ToolExecutionRequest> requests = ai.toolExecutionRequests();
            boolean hasOrphanCall = false;
            if (requests != null && !requests.isEmpty()) {
                for (ToolExecutionRequest req : requests) {
                    if (req.id() != null && !answeredToolCallIds.contains(req.id())) {
                        hasOrphanCall = true;
                        break;
                    }
                }
            }
            if (!needsStripThinking && !hasOrphanCall) {
                continue;
            }
            List<ToolExecutionRequest> kept = requests == null ? null : requests.stream()
                    .filter(req -> req.id() == null || answeredToolCallIds.contains(req.id()))
                    .toList();
            result.set(i, rebuildAiMessage(ai, kept, needsStripThinking));
        }
        return result;
    }

    /**
     * 重建 AiMessage：可选剥离 thinking、可选用 {@code kept} 替换 toolExecutionRequests。
     * 若 text 为空且裁剪后 tool_calls 也为空，langchain4j AiMessage.builder 会抛参数异常，
     * 此时回退给一个占位文本，保证消息合法。
     */
    private AiMessage rebuildAiMessage(AiMessage ai, List<ToolExecutionRequest> kept, boolean stripThinking) {
        String text = ai.text();
        boolean noTools = kept == null || kept.isEmpty();
        boolean noText = text == null || text.isEmpty();
        if (noTools && noText) {
            text = "(omitted)";
        }
        AiMessage.Builder builder = AiMessage.builder()
                .text(text)
                .attributes(ai.attributes());
        if (!noTools) {
            builder.toolExecutionRequests(kept);
        }
        if (!stripThinking && ai.thinking() != null) {
            // 当前轮（lastUserIndex 之后）保留 thinking：DeepSeek thinking 模式硬要求
            builder.thinking(ai.thinking());
        }
        return builder.build();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
