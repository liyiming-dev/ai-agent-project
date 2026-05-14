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
import java.util.regex.Pattern;

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
     * 工具/后台/管理类需求关键词：命中即跳过槽位插入，避免 hero+feature 模板把
     * 复杂业务页面(订单列表/表格/控制台)硬掰成营销页骨架。
     * 这些场景里"无 stock 图片"是更合适的默认值，模型可以专注业务结构。
     */
    private static final Pattern APP_LIKE_CONTENT_PATTERN = Pattern.compile(
            "(后台|管理系统|管理平台|管理端|运营平台|控制台|工作台|driver|dashboard|admin|cms|erp|crm|"
                    + "计算器|转换器|编辑器|editor|todo|待办|任务列表|表格|计步器|计时器|时钟|"
                    + "数据可视化|报表|看板|debugger|ide)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 根据用户提示词与生成类型产出基线槽位计划。
     * <p>marketing/landing 类需求：保留 hero + feature 槽位 + 营销布局建议。
     * <p>app-like(后台/工具)类需求：返回零槽位 + 中性布局建议，让模型按真实业务结构组织代码。
     */
    public ImageSlotPlan buildHeuristicPlan(String userMessage, CodeGenTypeEnum codeGenType) {
        if (isAppLikeContent(userMessage)) {
            return ImageSlotPlan.builder()
                    .slots(new ArrayList<>())
                    .layoutGuidance("根据业务结构自由组织页面：表格、表单、列表、卡片等结构化区块优先，无需预设营销 hero/feature 模板，也不需要使用 stock 图片填充。")
                    .source(SlotPlanSource.HEURISTIC)
                    .build();
        }
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

    private static boolean isAppLikeContent(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return false;
        }
        return APP_LIKE_CONTENT_PATTERN.matcher(userMessage).find();
    }

    /**
     * 营销/官网/介绍类页面的布局建议：给出"丰富度清单"，避免模型仅产出 hero + 3 个 feature
     * 就当成完成。槽位只有 2 个(hero+feature),其余位置鼓励自创占位符 slotId（系统会兜底为占位图）。
     * <p>app-like(后台/工具)类需求在 buildHeuristicPlan 入口就走了零槽位分支，此处不会被命中。
     */
    private static String buildLayoutGuidance(CodeGenTypeEnum codeGenType) {
        String base = "页面必须结构丰富，至少包含以下 6-8 个语义区块（按业务类型挑选并填充真实内容，禁止只写 hero + 几张 feature 卡片就结束）：\n"
                + "  1) 顶部导航（含品牌名 + 多个锚点链接）\n"
                + "  2) 首屏 Hero：使用 __IMG_SLOT_hero_main_1__ 作为主视觉大图，含标题、副标题、CTA 按钮\n"
                + "  3) 关于/简介：1-2 段实际文案，说明业务/作者背景\n"
                + "  4) 核心内容板块（按业务展开多项，每项至少 3-6 个条目）：\n"
                + "     - 咖啡店/餐饮：菜单分组 + 多个菜品卡片（名称/描述/价格/配图）\n"
                + "     - 个人博客：文章列表 + 多篇博文卡片（标题/摘要/日期/封面）+ 分类标签\n"
                + "     - 作品集：项目网格 + 多个项目卡片（标题/技术栈/截图）\n"
                + "     - 公司官网：服务/产品矩阵 + 多张卡片 + 数据指标\n"
                + "  5) 画廊/环境/案例展示：多张图片网格，使用 __IMG_SLOT_feature_1__ 与自创占位符（如 __IMG_SLOT_gallery_1__、__IMG_SLOT_gallery_2__）\n"
                + "  6) 用户评价/客户案例/数据指标（任选 1-2 项）\n"
                + "  7) 联系方式 / 营业时间 / 地图 / 表单\n"
                + "  8) 页脚（含社交链接 + 版权）\n"
                + "占位符不够时按 __IMG_SLOT_<下划线名> __ 自创即可，系统会兜底为占位图。";
        if (codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            return base + "\n多文件场景下，特性区/装饰区也可使用 CSS background-image 引用占位符（同一 slotId 可在 HTML 与 CSS 中共用）。";
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
