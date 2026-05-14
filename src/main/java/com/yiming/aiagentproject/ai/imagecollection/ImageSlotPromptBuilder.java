package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlot;
import com.yiming.aiagentproject.ai.imagecollection.model.ImageSlotPlan;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 把 {@link ImageSlotPlan} 拼成首轮 prompt 用的"图片设计槽位"段落。
 *
 * <p>token 预算（设计文档第五节）：
 * HTML / MULTI_FILE 段落总长 ≤ 1500 字；超长时从尾部砍 LOGO/ILLUSTRATION 类槽位。
 * 阶段 1 只对 HTML 启用；MULTI_FILE / Vue 后续阶段再接入。
 */
@Slf4j
public final class ImageSlotPromptBuilder {

    /**
     * HTML / MULTI_FILE 槽位段落的字符上限。
     * <p>1500 字符在加入"营销页 6-8 区块丰富度清单"后会被卡裁，造成 layoutGuidance 在 trim 路径里丢失。
     * 上调到 2200：实际首轮 prompt 仍远低于流式输入预算，TTFT 没有可观测影响。
     */
    private static final int HTML_MULTI_BUDGET_CHARS = 2200;

    private ImageSlotPromptBuilder() {
    }

    /**
     * 在原始 userMessage 末尾追加图片槽位段落。
     * 计划为空时返回原 userMessage，不报错。
     */
    public static String buildPromptWithImageSlots(String userMessage, ImageSlotPlan plan) {
        if (plan == null || CollUtil.isEmpty(plan.getSlots())) {
            return userMessage == null ? "" : userMessage;
        }
        String slotSection = buildSlotSection(plan, HTML_MULTI_BUDGET_CHARS);
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotEmpty(userMessage)) {
            builder.append(userMessage);
            if (!userMessage.endsWith("\n")) {
                builder.append("\n");
            }
        }
        builder.append("\n").append(slotSection);
        return builder.toString();
    }

    /**
     * 构造槽位段落。超过 budget 时从末尾砍掉 required=false 的 slot；仍超则截断。
     */
    private static String buildSlotSection(ImageSlotPlan plan, int budget) {
        List<ImageSlot> slots = plan.getSlots();
        StringBuilder section = new StringBuilder();
        section.append("## 图片设计槽位\n");
        section.append("请在页面设计阶段为以下图片槽位预留自然位置，真实图片稍后绑定，当前不要随意编造图片 URL。\n\n");
        for (ImageSlot slot : slots) {
            appendSlot(section, slot);
        }
        if (StrUtil.isNotBlank(plan.getLayoutGuidance())) {
            section.append("\n布局建议：").append(plan.getLayoutGuidance()).append("\n");
        }
        section.append("\n占位规则（必须严格遵守）：\n");
        section.append("- HTML <img>: src=\"__IMG_SLOT_<slotId>__\"\n");
        section.append("- Vue <img>: :src=\"imageAssets.<camelSlotId>.url\" 或 src=\"__IMG_SLOT_<slotId>__\"\n");
        section.append("- CSS background-image: background-image: url(\"__IMG_SLOT_<slotId>__\")\n");
        section.append("- 不允许使用 picsum.photos / unsplash 直链 / data: 协议 / 任何随机图作为槽位的替代\n");
        section.append("- 不允许把占位符出现在 HTML 注释、JS 字符串拼接或模板字面量中\n");
        section.append("- 不允许在自然语言说明、解释、回复或建议中出现 __IMG_SLOT_* 占位符字符串；占位符仅出现在代码块内\n");
        section.append("- 给用户的文字说明里不要提及占位符的替换、绑定或工程化路径，相关替换由系统自动完成\n");
        if (section.length() > budget) {
            log.info("槽位段落超出预算 {} 字符，触发裁剪", budget);
            return trimToBudget(plan, budget);
        }
        return section.toString();
    }

    private static void appendSlot(StringBuilder section, ImageSlot slot) {
        if (slot == null || StrUtil.isEmpty(slot.getSlotId())) {
            return;
        }
        section.append("- slotId: ").append(slot.getSlotId()).append("\n");
        if (slot.getCategory() != null) {
            section.append("  category: ").append(slot.getCategory().getValue()).append("\n");
        }
        if (StrUtil.isNotBlank(slot.getPage())) {
            section.append("  page: ").append(slot.getPage()).append("\n");
        }
        if (StrUtil.isNotBlank(slot.getSection())) {
            section.append("  section: ").append(slot.getSection()).append("\n");
        }
        if (StrUtil.isNotBlank(slot.getAspectRatio())) {
            section.append("  aspectRatio: ").append(slot.getAspectRatio()).append("\n");
        }
        if (StrUtil.isNotBlank(slot.getAlt())) {
            section.append("  alt: ").append(slot.getAlt()).append("\n");
        }
    }

    /**
     * 预算超限时的裁剪：先丢弃 required=false 的槽位，仍超则硬截断。
     */
    private static String trimToBudget(ImageSlotPlan plan, int budget) {
        ImageSlotPlan trimmed = ImageSlotPlan.builder()
                .layoutGuidance(plan.getLayoutGuidance())
                .source(plan.getSource())
                .slots(plan.getSlots().stream().filter(ImageSlot::isRequired).toList())
                .build();
        StringBuilder section = new StringBuilder();
        section.append("## 图片设计槽位\n");
        for (ImageSlot slot : trimmed.getSlots()) {
            appendSlot(section, slot);
        }
        if (section.length() > budget) {
            return section.substring(0, budget);
        }
        return section.toString();
    }
}
