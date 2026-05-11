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
 * <p>阶段 1：HTML 首轮接入。
 * <p>阶段 2：MULTI_FILE 接入，与 HTML 同分桶（{@code hero_main_1} + {@code feature_1}），
 * 占位符在 ```html``` / ```css``` 代码块内出现，由共用的
 * {@link ImageSlotBinder} / {@link PlaceholderFallbackScrubber} 处理。
 * <p>VUE_PROJECT 后续阶段再扩展。
 */
@Component
public class HeuristicSlotPlanner {

    /**
     * 根据用户提示词与生成类型产出基线槽位计划。
     * 任意生成类型至少返回 1 个 hero 槽位，避免"零槽位"。
     */
    public ImageSlotPlan buildHeuristicPlan(String userMessage, CodeGenTypeEnum codeGenType) {
        List<ImageSlot> slots = new ArrayList<>();
        slots.add(buildHeroSlot(userMessage));
        // HTML 与 MULTI_FILE 共用同一最简分桶：hero + feature；输出形态都是
        // markdown 代码块，占位符语义一致，复用同一套绑定/兜底链路。
        if (codeGenType == null
                || codeGenType == CodeGenTypeEnum.HTML
                || codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            slots.add(buildFeatureSlot(userMessage));
        }
        return ImageSlotPlan.builder()
                .slots(slots)
                .layoutGuidance(buildLayoutGuidance(codeGenType))
                .source(SlotPlanSource.HEURISTIC)
                .build();
    }

    /**
     * 按生成类型给布局建议加一点引导。MULTI_FILE 时显式提示模型可在 CSS
     * {@code background-image} 中使用占位符——这覆盖了"是否包含 CSS 替换分支"
     * 的语义,binder 正则本身已经能匹配 {@code url(...)} 里的占位符。
     */
    private static String buildLayoutGuidance(CodeGenTypeEnum codeGenType) {
        String base = "首屏需要预留主视觉大图区域，特性区可使用一张配图卡片。";
        if (codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            return base + " 多文件场景下，特性区/装饰区也可使用 CSS background-image 引用占位符（同一 slotId 可在 HTML 与 CSS 中共用）。";
        }
        return base;
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
