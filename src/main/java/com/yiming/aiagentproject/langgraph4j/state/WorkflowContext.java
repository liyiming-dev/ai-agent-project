package com.yiming.aiagentproject.langgraph4j.state;

import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageCollectionPlan;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.langgraph4j.model.QualityResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 并发图片收集分支在 MessagesState 中各自独立的存储 key，
     * 避免多个并行节点同时写同一个 WorkflowContext 实例引发竞态。
     * 由 ImageAggregatorNode 在汇聚阶段读取并合并到 WorkflowContext.imageList。
     */
    public static final String CONTENT_IMAGES_KEY = "contentImages";
    public static final String ILLUSTRATIONS_KEY = "illustrations";
    public static final String DIAGRAMS_KEY = "diagrams";
    public static final String LOGOS_KEY = "logos";

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 代码生成类型
     */
    private CodeGenTypeEnum generationType;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;


    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
