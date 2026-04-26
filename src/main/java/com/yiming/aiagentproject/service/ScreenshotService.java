package com.yiming.aiagentproject.service;

public interface ScreenshotService {

    /**
     * 根据网页 URL 生成截图并上传到 OSS，返回可访问的 URL
     */
    String generateAndUploadScreenshot(String webUrl);
}
