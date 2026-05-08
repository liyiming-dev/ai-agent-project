package com.yiming.aiagentproject.ai.tools;

import cn.hutool.json.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yiming.aiagentproject.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool{

    /**
     * 失配尝试缓存：key = appId + filePath + oldContent 哈希；value = 已尝试次数。
     * 用来识别"AI 反复用相近的 oldContent 试图替换同一个文件"这种最常见的工具循环模式，
     * 一旦同一组合二次命中就拒绝执行，并指导 AI 改用 readFile + writeFile 重写。
     */
    private final Cache<String, Integer> failedAttemptCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Tool("修改文件内容，用新内容替换指定的旧内容。oldContent 必须与文件中的字节完全一致（含缩进、换行、转义）。同一处修改失败后请改用 readFile 读取最新内容再决定下一步，禁止凭记忆反复重试。")
    public String modifyFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容；必须是文件中存在的、字节完全一致的一段文本")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath
                        + "。请先用 readFile 工具读取项目结构和文件内容确认路径，不要再用相同参数重试本工具。";
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)) {
                String key = buildAttemptKey(appId, relativeFilePath, oldContent);
                int attempts = failedAttemptCache.get(key, k -> 0) + 1;
                failedAttemptCache.put(key, attempts);
                if (attempts >= 2) {
                    return String.format(
                            "错误：对同一文件同一段 oldContent 已连续 %d 次未找到匹配，禁止继续用 modifyFile 重试。"
                            + "请改用以下任一方案："
                            + "(1) 调用 readFile 读取 %s 的最新内容，使用文件中真实存在的字节段落作为 oldContent；"
                            + "(2) 若需要整体替换，直接用 writeFile 重写整个文件。"
                            + "切勿再用近似 oldContent 反复试错。",
                            attempts, relativeFilePath);
                }
                return "警告：在 " + relativeFilePath + " 中未找到 oldContent。"
                        + "可能原因：缩进/换行/转义与文件实际字节不一致。请先调用 readFile 读取该文件最新内容，"
                        + "用文件中真实存在的连续字节段作为 oldContent；不要靠记忆猜测或微调写法重试本工具。";
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "信息：oldContent 与 newContent 等价，文件内容未发生变化 - " + relativeFilePath
                        + "。无需重复调用本工具。";
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // 修改成功后清除该文件的失配计数，避免误伤后续合法编辑
            failedAttemptCache.invalidate(buildAttemptKey(appId, relativeFilePath, oldContent));
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    private String buildAttemptKey(Long appId, String relativeFilePath, String oldContent) {
        // 用 oldContent 的 hashCode 即可区分不同片段，整段拼进 key 会让 cache value 过大
        return appId + "::" + relativeFilePath + "::" + Integer.toHexString(oldContent.hashCode());
    }


        // 核心方法不变，此处省略

        @Override
        public String getToolName() {
            return "modifyFile";
        }

        @Override
        public String getDisplayName() {
            return "修改文件";
        }

        @Override
        public String generateToolExecutedResult(JSONObject arguments) {
            String relativeFilePath = arguments.getStr("relativeFilePath");
            String oldContent = arguments.getStr("oldContent");
            String newContent = arguments.getStr("newContent");
            // 显示对比内容
            return String.format("""
                [工具调用] %s %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
        }


}
