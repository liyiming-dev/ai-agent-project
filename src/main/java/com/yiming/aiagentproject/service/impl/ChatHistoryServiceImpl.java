package com.yiming.aiagentproject.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yiming.aiagentproject.model.entity.ChatHistory;
import com.yiming.aiagentproject.mapper.ChatHistoryMapper;
import com.yiming.aiagentproject.service.ChatHistoryService;
import org.springframework.stereotype.Service;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

}
