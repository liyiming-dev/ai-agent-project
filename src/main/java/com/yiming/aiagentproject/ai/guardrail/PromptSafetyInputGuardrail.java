package com.yiming.aiagentproject.ai.guardrail;

import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class PromptSafetyInputGuardrail implements InputGuardrail {

    /**
     * 用户原始输入最大长度。仅作用于用户手动输入的提示词，
     * 不覆盖系统在内部增强后的提示词（增强后可能上万字符）。
     */
    private static final int MAX_RAW_USER_PROMPT_LENGTH = 2000;

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    /**
     * 校验用户原始输入。应在所有提示词增强逻辑之前调用，
     * 仅校验用户手动输入的内容；不应用于已被系统增强后的提示词。
     */
    public static void validateRawUserPrompt(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入内容不能为空");
        }
        if (input.length() > MAX_RAW_USER_PROMPT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "输入内容过长，不要超过 " + MAX_RAW_USER_PROMPT_LENGTH + " 字");
        }
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入包含不当内容，请修改后重试");
            }
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "检测到恶意输入，请求被拒绝");
            }
        }
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        try {
            validateRawUserPrompt(userMessage.singleText());
            return success();
        } catch (BusinessException e) {
            return fatal(e.getMessage());
        }
    }
}
