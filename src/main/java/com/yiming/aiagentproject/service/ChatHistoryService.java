package com.yiming.aiagentproject.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yiming.aiagentproject.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.yiming.aiagentproject.model.entity.ChatHistory;
import com.yiming.aiagentproject.model.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/liyiming">yiming</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    boolean deleteByAppId(Long appId);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
}
