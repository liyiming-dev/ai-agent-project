package com.yiming.aiagentproject.ai.imagecollection.model;

import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片槽位：首轮 prompt 与素材绑定共用的"合同"。
 *
 * <p>{@code slotId} 必须稳定、短、可读，命名规范为 {@code <section>_<role>_<seq>}，
 * 如 {@code hero_main_1}、{@code feature_card_2}；模型输出与后端绑定都以此为唯一锚点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSlot implements Serializable {

    /** 稳定占位符 id，例如 hero_main_1。 */
    private String slotId;

    /** 图片类别，决定派发到哪个收集工具。 */
    private ImageCategoryEnum category;

    /** 页面或路由，例如 home / about / contact。 */
    private String page;

    /** 区块，例如 hero / product-card / workflow。 */
    private String section;

    /** 搜索关键词或生成描述。 */
    private String query;

    /** alt 文案。 */
    private String alt;

    /** 长宽比，例如 16:9 / 4:3 / 1:1 / logo。 */
    private String aspectRatio;

    /** 是否强依赖（缺图时必须兜底）。 */
    private boolean required;

    @Serial
    private static final long serialVersionUID = 1L;
}
