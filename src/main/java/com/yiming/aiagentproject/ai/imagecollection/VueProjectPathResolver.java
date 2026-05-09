package com.yiming.aiagentproject.ai.imagecollection;

import com.yiming.aiagentproject.constant.AppConstant;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vue 工程根目录解析器：根据 appId 推算 tmp/code_output/vue_project_{appId} 的根路径。
 * 与 {@code FileWriteTool}/{@code FileReadTool} 等保持同一约定，避免重复字面量。
 */
public final class VueProjectPathResolver {

    private static final String VUE_PROJECT_DIR_PREFIX = "vue_project_";

    private VueProjectPathResolver() {
    }

    /**
     * 解析 appId 对应的 Vue 工程根目录 Path。
     */
    public static Path resolveProjectRoot(Long appId) {
        if (appId == null) {
            return null;
        }
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, VUE_PROJECT_DIR_PREFIX + appId);
    }

    /**
     * 解析 appId 对应的 Vue 工程根目录 File。
     */
    public static File resolveProjectRootFile(Long appId) {
        Path path = resolveProjectRoot(appId);
        return path == null ? null : path.toFile();
    }

    /**
     * 解析 Vue 工程下的 src/ 目录。
     */
    public static File resolveSrcDir(Long appId) {
        Path root = resolveProjectRoot(appId);
        return root == null ? null : root.resolve("src").toFile();
    }
}
