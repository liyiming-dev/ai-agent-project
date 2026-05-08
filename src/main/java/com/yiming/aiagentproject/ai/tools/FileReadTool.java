package com.yiming.aiagentproject.ai.tools;

import cn.hutool.json.JSONObject;
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

/**
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool{

    /**
     * 单次返回给模型的最大字符数。
     * 超过将截断并提示模型改用片段化策略。
     * 防止 readFile 整段大文件回写到 chat memory，撑大下一轮 prompt（Vue 工具循环越走越慢的次要原因）。
     */
    private static final int MAX_RETURN_CHARS = 8000;

    @Tool("读取指定路径的文件内容。返回内容若超过约 8KB 将被截断，仅返回开头部分；如需修改大文件请改用 modifyFile 按片段操作，禁止反复 read 同一文件以求阅读全文。")
    public String readFile(
            @P("文件的相对路径")
            String relativeFilePath,
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
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            String content = Files.readString(path);
            if (content.length() <= MAX_RETURN_CHARS) {
                return content;
            }
            String head = content.substring(0, MAX_RETURN_CHARS);
            return head + "\n\n[文件被截断：原文 " + content.length() + " 字符，仅返回前 "
                    + MAX_RETURN_CHARS + " 字符。如需修改请直接基于已知信息用 modifyFile 替换具体片段，"
                    + "或用 writeFile 整体重写；不要为读完全文反复调用本工具。]";
        } catch (IOException e) {
            String errorMessage = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }


        // 核心方法不变，此处省略

        @Override
        public String getToolName() {
            return "readFile";
        }

        @Override
        public String getDisplayName() {
            return "读取文件";
        }

        @Override
        public String generateToolExecutedResult(JSONObject arguments) {
            String relativeFilePath = arguments.getStr("relativeFilePath");
            return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
        }


}
