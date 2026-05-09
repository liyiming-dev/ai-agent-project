package com.yiming.aiagentproject.ai.imagecollection.model;

/**
 * 图片槽位计划来源：标识 ImageSlotPlan 是纯启发式产出，还是经过 LLM 增强。
 */
public enum SlotPlanSource {
    /** 本地启发式产出（同步、< 10ms）。 */
    HEURISTIC,
    /** 启发式基线 + LLM 短等增强叠加产出。 */
    LLM_ENHANCED
}
