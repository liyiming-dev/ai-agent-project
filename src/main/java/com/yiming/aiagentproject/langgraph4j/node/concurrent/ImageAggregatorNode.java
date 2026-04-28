package com.yiming.aiagentproject.langgraph4j.node.concurrent;

import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片聚合节点
 */
@Slf4j
public class ImageAggregatorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Map<String, Object> data = state.data();
            List<ImageResource> allImages = new ArrayList<>();
            log.info("开始聚合并发收集的图片");
            addAllIfPresent(allImages, data, WorkflowContext.CONTENT_IMAGES_KEY);
            addAllIfPresent(allImages, data, WorkflowContext.ILLUSTRATIONS_KEY);
            addAllIfPresent(allImages, data, WorkflowContext.DIAGRAMS_KEY);
            addAllIfPresent(allImages, data, WorkflowContext.LOGOS_KEY);
            log.info("图片聚合完成，总共 {} 张图片", allImages.size());
            context.setImageList(allImages);
            context.setCurrentStep("图片聚合");
            return WorkflowContext.saveContext(context);
        });
    }

    @SuppressWarnings("unchecked")
    private static void addAllIfPresent(List<ImageResource> target, Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list) {
            target.addAll((List<ImageResource>) list);
        }
    }
}
