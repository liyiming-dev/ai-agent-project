package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlot;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlotPlan;
import com.yiming.aiagentproject.ai.imagecollection.model.SlotPlanSource;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地启发式图片槽位计划器：同步、纯本地、不调模型，全局 < 10ms。
 *
 * <p>阶段 1 仅支持 HTML 首轮，最简分桶：{@code hero_main_1} + {@code feature_1}。
 * 后续阶段会扩展 MULTI_FILE / VUE_PROJECT / 多轮持久化等分支。
 */
@Component
public class HeuristicSlotPlanner {

    /**
     * 根据用户提示词与生成类型产出基线槽位计划。
     * 任意生成类型至少返回 1 个 hero 槽位，避免"零槽位"。
     */
    public ImageSlotPlan buildHeuristicPlan(String userMessage, CodeGenTypeEnum codeGenType) {
        List<ImageSlot> slots = new ArrayList<>();
        // 阶段 1：HTML 首轮固定分桶 hero_main_1 + feature_1。
        // MULTI_FILE / VUE 后续阶段再扩展，这里先按最稳的 HTML 一致策略给基线。
        slots.add(buildHeroSlot(userMessage));
        if (codeGenType == null || codeGenType == CodeGenTypeEnum.HTML) {
            slots.add(buildFeatureSlot(userMessage));
        }
        return ImageSlotPlan.builder()
                .slots(slots)
                .layoutGuidance("首屏需要预留主视觉大图区域，特性区可使用一张配图卡片。")
                .source(SlotPlanSource.HEURISTIC)
                .build();
    }

    private ImageSlot buildHeroSlot(String userMessage) {
        return ImageSlot.builder()
                .slotId("hero_main_1")
                .category(ImageCategoryEnum.CONTENT)
                .page("home")
                .section("hero")
                .aspectRatio("16:9")
                .alt(buildAltFromUserMessage(userMessage, "首页主视觉"))
                .query(buildQueryFromUserMessage(userMessage, "首页主视觉 hero 大图"))
                .required(true)
                .build();
    }

    private ImageSlot buildFeatureSlot(String userMessage) {
        return ImageSlot.builder()
                .slotId("feature_1")
                .category(ImageCategoryEnum.CONTENT)
                .page("home")
                .section("feature")
                .aspectRatio("4:3")
                .alt(buildAltFromUserMessage(userMessage, "特性配图"))
                .query(buildQueryFromUserMessage(userMessage, "特性卡片配图"))
                .required(false)
                .build();
    }

    private static String buildAltFromUserMessage(String userMessage, String suffix) {
        if (StrUtil.isBlank(userMessage)) {
            return suffix;
        }
        String trimmed = userMessage.length() > 30 ? userMessage.substring(0, 30) : userMessage;
        return trimmed + " " + suffix;
    }

    private static String buildQueryFromUserMessage(String userMessage, String fallback) {
        if (StrUtil.isBlank(userMessage)) {
            return fallback;
        }
        String trimmed = userMessage.length() > 60 ? userMessage.substring(0, 60) : userMessage;
        return trimmed + " " + fallback;
    }
}
