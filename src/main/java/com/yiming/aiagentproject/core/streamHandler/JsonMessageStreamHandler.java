package com.yiming.aiagentproject.core.streamHandler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yiming.aiagentproject.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.yiming.aiagentproject.ai.model.enums.StreamMessageTypeEnum;
import com.yiming.aiagentproject.ai.model.message.AiResponseMessage;
import com.yiming.aiagentproject.ai.model.message.StreamMessage;
import com.yiming.aiagentproject.ai.model.message.ToolExecutedMessage;
import com.yiming.aiagentproject.ai.model.message.ToolRequestMessage;
import com.yiming.aiagentproject.ai.tools.BaseTool;
import com.yiming.aiagentproject.ai.tools.ToolManager;
import com.yiming.aiagentproject.constant.AppConstant;
import com.yiming.aiagentproject.core.builder.VueProjectBuilder;
import com.yiming.aiagentproject.model.entity.User;
import com.yiming.aiagentproject.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        // 标记当前是否打开了 tool args 的 ```json 代码围栏：
        // 首次 TOOL_REQUEST 打开，TOOL_EXECUTED 关闭。中途 EXECUTED 缺失时由下一次 TOOL_REQUEST 自愈补关。
        AtomicBoolean argsFenceOpen = new AtomicBoolean(false);
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds, argsFenceOpen);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .concatWith(Flux.defer(() -> {
                    // AI 生成完毕：先落库聊天历史，再同步执行 Vue 构建，最后才让流 complete
                    // 这样 Controller 在 Flux 结束后补发的 done 事件到达前端时，dist/ 已就绪
                    String aiResponse = chatHistoryStringBuilder.toString();

                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    //异步构建 Vue项目
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                    // 流尾自愈：若上一个 tool args fence 未被 EXECUTED 关掉(异常/截断场景),
                    // 先补一个闭合,避免后续"[构建 Vue 项目]..."文本被卷入 ```json 代码块
                    String fenceClose = argsFenceOpen.getAndSet(false) ? "\n```\n" : "";
                    return Flux.concat(
                            Flux.just(fenceClose + "\n\n[构建 Vue 项目] 正在执行 npm install 和 npm run build，请稍候...\n\n"),
                            Mono.fromCallable(() -> vueProjectBuilder.buildProject(projectPath))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .map(success -> success
                                            ? "\n\n[构建成功] 预览已就绪\n\n"
                                            : "\n\n[构建失败] 请查看后端日志\n\n")
                                    .onErrorResume(e -> Mono.just("\n\n[构建异常] " + e.getMessage() + "\n\n"))
                    );
                }))
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     *
     * <p>TOOL_REQUEST 分支历史上对同 toolId 的后续 chunk 一律返回空串，导致前端在工具参数流式期间长时间
     * 黑屏（DeepSeek 写一个大文件时 args 流式可能持续 30s+）。现在把后续 partial args 透传给前端，
     * 用 ```json 代码围栏包裹，让用户实时看到 tool 参数（即写入的文件内容）在增长。
     * 聊天历史 builder 不收 args，避免污染 DB 历史，存储侧仍只保留 AI 文本和工具执行摘要。
     */
    private String handleJsonMessageChunk(String chunk,
                                          StringBuilder chatHistoryStringBuilder,
                                          Set<String> seenToolIds,
                                          AtomicBoolean argsFenceOpen) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                String partialArgs = StrUtil.nullToEmpty(toolRequestMessage.getArguments());
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    seenToolIds.add(toolId);
                    BaseTool tool = toolManager.getTool(toolName);
                    StringBuilder out = new StringBuilder();
                    // 防御：上一段 fence 没被 EXECUTED 关掉就先关，避免围栏嵌套
                    if (argsFenceOpen.getAndSet(true)) {
                        out.append("\n```\n");
                    }
                    out.append(tool.generateToolRequestResponse());
                    out.append("```json\n");
                    out.append(partialArgs);
                    return out.toString();
                }
                // 同 toolId 后续 partial args 透传：让前端看到写入内容流式增长
                return partialArgs;
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                String historyOutput = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(historyOutput);
                // 关闭 args 代码围栏（若已打开），再追加执行结果
                String fenceClose = argsFenceOpen.getAndSet(false) ? "\n```\n" : "";
                return fenceClose + historyOutput;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }


}


