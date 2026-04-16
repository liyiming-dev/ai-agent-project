package com.yiming.aiagentproject.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static com.yiming.aiagentproject.constant.AppConstant.CODE_DEPLOY_ROOT_DIR;
import static com.yiming.aiagentproject.constant.AppConstant.CODE_OUTPUT_ROOT_DIR;

@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 候选根目录：优先查生成目录（对话页预览），其次查部署目录（部署后访问）
    private static final String[] PREVIEW_ROOT_DIRS = {CODE_OUTPUT_ROOT_DIR, CODE_DEPLOY_ROOT_DIR};

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     * 其中 deployKey 可以是生成目录名（{codeGenType}_{appId}）或部署目录名（随机码）
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 从 URI 中提取 /static/{deployKey} 之后的子路径（去掉 contextPath）
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }
            String prefix = "/static/" + deployKey;
            String resourcePath = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";
            resourcePath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 依次在候选根目录中查找文件
            File file = null;
            for (String rootDir : PREVIEW_ROOT_DIRS) {
                File candidate = new File(rootDir + "/" + deployKey + resourcePath);
                if (candidate.exists() && candidate.isFile()) {
                    file = candidate;
                    break;
                }
            }
            // 检查文件是否存在
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(file.getName()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
