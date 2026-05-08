package com.yiming.aiagentproject.exception;

import cn.hutool.json.JSONUtil;
import com.yiming.aiagentproject.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (handleSseError(e.getCode(), e.getMessage())) {
            // SSE 已就地写入，避免 Spring 再走一次 MessageConverter（Content-Type=text/event-stream 时会失败）
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误"));
    }

    /**
     * 处理 SSE 请求的错误响应。
     * <p>
     * 注意：Spring 在响应 {@code Flux<ServerSentEvent<...>>} 时已经通过
     * {@link HttpServletResponse#getOutputStream()} 写入字节流，所以这里必须用
     * {@code getOutputStream()} 续写，不能用 {@code getWriter()}（会抛
     * {@code IllegalStateException: getOutputStream() has already been called}）。
     *
     * @return true 表示是 SSE 请求（无论写入是否成功，调用方都不应再返回 JSON 响应体）
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        boolean isSse = (accept != null && accept.contains("text/event-stream"))
                || (uri != null && uri.contains("/chat/gen/code"));
        if (!isSse) {
            return false;
        }
        try {
            // 仅当响应尚未提交且 Content-Type 还未设置时才设置头；已开始流式输出的响应不能再改头
            if (!response.isCommitted()) {
                if (response.getContentType() == null) {
                    response.setContentType("text/event-stream");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader("Cache-Control", "no-cache");
                    response.setHeader("Connection", "keep-alive");
                }
            }
            Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
            );
            String sseData = "event: business-error\ndata: " + JSONUtil.toJsonStr(errorData) + "\n\n"
                    + "event: done\ndata: {}\n\n";
            ServletOutputStream out = response.getOutputStream();
            out.write(sseData.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception writeEx) {
            // 客户端断连、流已关闭、getWriter/getOutputStream 冲突等都到这里——不再向上抛
            log.warn("写入 SSE 错误事件失败（可能客户端已断开或响应已结束）: {}", writeEx.toString());
        }
        return true;
    }
}
