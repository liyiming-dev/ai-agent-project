package com.yiming.aiagentproject.ai.imagecollection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 图片槽位计划：首轮 prompt 与素材异步收集共用的唯一真源。
 *
 * <p>素材收集任务必须由本计划的 {@code slots} 派生，不允许各工具独立生成 plan，
 * 避免"启发式槽位"与"实际派发任务"两条信息流不一致导致绑定率为 0。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSlotPlan implements Serializable {

    /** 槽位列表（顺序即推荐使用顺序）。 */
    private List<ImageSlot> slots;

    /** 整体图片布局建议；不允许塞真实 URL。 */
    private String layoutGuidance;

    /** 计划来源：HEURISTIC / LLM_ENHANCED。 */
    private SlotPlanSource source;

    @Serial
    private static final long serialVersionUID = 1L;
}
