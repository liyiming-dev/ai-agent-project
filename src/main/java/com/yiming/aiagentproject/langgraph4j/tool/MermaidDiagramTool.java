package com.yiming.aiagentproject.langgraph4j.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import com.yiming.aiagentproject.utils.AliyunOSSOperator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * mermaid架构图生成工具
 */
@Slf4j
@Component
public class MermaidDiagramTool {

    @Resource
    private AliyunOSSOperator aliyunOSSOperator;
    
    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                                      @P("架构图描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        File diagramFile = null;
        try {
            // 转换为SVG图片
            diagramFile = convertMermaidToSvg(mermaidCode);
            String diagramUrl = uploadDiagramToOss(diagramFile);
            if (StrUtil.isNotBlank(diagramUrl)) {
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(diagramUrl)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        } finally {
            cleanupTempFile(diagramFile);
        }
        return new ArrayList<>();
    }

    /**
     * 上传架构图到 OSS
     */
    private String uploadDiagramToOss(File diagramFile) {
        if (diagramFile == null || !diagramFile.exists()) {
            log.error("Mermaid架构图文件不存在");
            return null;
        }
        String fileName = RandomUtil.randomString(8) + ".svg";
        String objectKey = generateDiagramKey(fileName);
        return aliyunOSSOperator.upload(diagramFile, objectKey);
    }

    /**
     * 生成架构图的对象存储键
     * 格式：mermaid-diagrams/2025/07/31/filename.svg
     */
    private String generateDiagramKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("mermaid-diagrams/%s/%s", datePath, fileName);
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            FileUtil.del(tempFile);
            log.info("Mermaid架构图临时文件已清理: {}", tempFile.getAbsolutePath());
        }
    }

    /**
     * 将Mermaid代码转换为SVG图片
     */
    private File convertMermaidToSvg(String mermaidCode) {
        // 创建临时输入文件
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        // 创建临时输出文件
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        try {
            // 根据操作系统选择命令
            String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
            // 执行命令
            RuntimeUtil.execForStr(command, "-i", tempInputFile.getAbsolutePath(), "-o",
                    tempOutputFile.getAbsolutePath(), "-b", "transparent");
            // 检查输出文件
            if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
            }
            return tempOutputFile;
        } catch (Exception e) {
            FileUtil.del(tempOutputFile);
            throw e;
        } finally {
            FileUtil.del(tempInputFile);
        }
    }
}
