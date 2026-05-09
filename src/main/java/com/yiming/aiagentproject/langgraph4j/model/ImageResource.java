package com.yiming.aiagentproject.langgraph4j.model;

import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片资源对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResource implements Serializable {

    /**
     * 图片类别
     */
    private ImageCategoryEnum category;

    /**
     * 图片描述
     */
    private String description;

    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片槽位 id：对应 ImageSlotPlan 中的 slotId，
     * 用于把异步收集到的素材精确绑定到首轮代码中的 __IMG_SLOT_*__ 占位符。
     * 旧链路（无槽位计划时）允许为空。
     */
    private String slotId;

    @Serial
    private static final long serialVersionUID = 1L;
}
