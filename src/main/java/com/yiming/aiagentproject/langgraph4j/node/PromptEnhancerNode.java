package com.yiming.aiagentproject.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 提示词增强节点
 */
@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            // 获取原始提示词和图片列表
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            // 构建增强后的提示词
            StringBuilder enhancedPromptBuilder = new StringBuilder();
            enhancedPromptBuilder.append(originalPrompt);
            // 如果有图片资源，则添加图片信息
            //兼容逻辑
            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append("以下素材按类别分组，请严格按各分组的使用规则嵌入；不符合规则的素材不要使用。\n");
                if (CollUtil.isNotEmpty(imageList)) {
                    appendImagesByCategory(enhancedPromptBuilder, imageList);
                } else {
                    enhancedPromptBuilder.append(imageListStr);
                }
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();
            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }

    private static void appendImagesByCategory(StringBuilder builder, List<ImageResource> imageList) {
        Map<ImageCategoryEnum, List<ImageResource>> grouped = imageList.stream()
                .filter(img -> img.getCategory() != null)
                .collect(Collectors.groupingBy(ImageResource::getCategory));
        for (ImageCategoryEnum category : ImageCategoryEnum.values()) {
            List<ImageResource> images = grouped.get(category);
            if (CollUtil.isEmpty(images)) {
                continue;
            }
            builder.append("\n### ").append(category.getText()).append("\n");
            builder.append(usageRuleOf(category)).append("\n");
            for (ImageResource image : images) {
                builder.append("- ")
                        .append(image.getDescription())
                        .append("（")
                        .append(image.getUrl())
                        .append("）\n");
            }
        }
    }

    private static String usageRuleOf(ImageCategoryEnum category) {
        return switch (category) {
            case CONTENT -> "用途：网站正文配图，可在卡片、列表、详情等内容区域使用。";
            case ILLUSTRATION -> "用途：装饰性插画，用于空状态、特性介绍等装饰位置。";
            case LOGO -> "用途：品牌标识，仅用于导航栏 / 页脚 / 关于页等品牌展示位。";
            case ARCHITECTURE -> "用途：仅当页面存在专门的「技术架构 / 工作原理 / 业务流程」等说明性区块时使用；"
                    + "禁止放入首页 Hero、Banner、导航栏、通用卡片或纯装饰位；若全站没有合适位置，请直接忽略本组素材。";
        };
    }
}
