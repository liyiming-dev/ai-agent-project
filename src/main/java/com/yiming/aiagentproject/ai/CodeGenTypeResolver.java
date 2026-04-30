package com.yiming.aiagentproject.ai;

import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.ai.model.CodeGenTypeRoutingResponse;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码生成类型解析器，统一封装 AI 路由与兜底策略。
 */
@Slf4j
@Component
public class CodeGenTypeResolver {

    private static final CodeGenTypeEnum DEFAULT_CODE_GEN_TYPE = CodeGenTypeEnum.HTML;

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    /**
     * 根据用户提示词路由代码生成类型，失败时返回默认类型。
     *
     * @param userPrompt 用户提示词
     * @return 代码生成类型
     */
    public CodeGenTypeEnum routeOrDefault(String userPrompt) {
        if (StrUtil.isBlank(userPrompt)) {
            log.warn("AI 智能路由提示词为空，使用默认类型: {}", DEFAULT_CODE_GEN_TYPE.getValue());
            return DEFAULT_CODE_GEN_TYPE;
        }
        try {
            CodeGenTypeRoutingResponse response = aiCodeGenTypeRoutingService.routeCodeGenType(userPrompt);
            CodeGenTypeEnum codeGenType = response == null ? null : response.getType();
            if (codeGenType == null) {
                log.warn("AI 智能路由返回类型为空，使用默认类型: {}", DEFAULT_CODE_GEN_TYPE.getValue());
                return DEFAULT_CODE_GEN_TYPE;
            }
            return codeGenType;
        } catch (Exception e) {
            log.error("AI 智能路由失败，使用默认类型: {}", DEFAULT_CODE_GEN_TYPE.getValue(), e);
            return DEFAULT_CODE_GEN_TYPE;
        }
    }
}
