package com.yiming.aiagentproject.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史 实体类。
 *
 * @author <a href="https://github.com/liyiming">yiming</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_history")
public class ChatHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 消息
     */
    private String message;

    /**
     * user/ai
     */
    @Column("messageType")
    private String messageType;

    /**
     * 应用id
     */
    @Column("appId")
    private Long appId;

    /**
     * 会话id(雪花)。同 appId 下按 sessionId 切分多轮对话,
     * 用户点"新对话"会刷新 app.currentSessionId,后续消息绑到新 session,
     * loadChatHistoryToMemory 只加载匹配 currentSessionId 的历史,杜绝跨主题污染。
     */
    @Column("sessionId")
    private Long sessionId;

    /**
     * 创建用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
