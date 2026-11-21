package com.yuluo.tourguide.exception;

import cn.hutool.json.JSONUtil;
import com.yuluo.tourguide.common.BaseResponse;
import com.yuluo.tourguide.common.ResultUtils;
import com.yuluo.tourguide.constant.SseConstant;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理SSE请求的错误响应
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是SSE请求并已处理，false表示不是SSE请求
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
        if ((accept != null && accept.contains("text/event-stream"))
                || (uri != null && uri.contains("/chat/gen/code"))) {
            if (response.isCommitted()) {
                // SSE 帧头已发出，此处再写只会与容器输出流互斥抛 IllegalStateException；流中错误的帧由 GuideApp 负责
                log.warn("SSE 响应已提交，错误帧无法送达，仅记录日志：code={} message={}", errorCode, errorMessage);
                return true;
            }
            try {
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                Map<String, Object> errorData = Map.of(
                        "error", true,
                        "code", errorCode,
                        "message", errorMessage
                );
                String errorJson = JSONUtil.toJsonStr(errorData);
                // 必须用 getOutputStream：SSE 的字节由框架写出器经 OutputStream 通道写出，
                // 此时 getWriter() 会抛 IllegalStateException（getOutputStream() has already been called），错误帧反而全丢
                OutputStream out = response.getOutputStream();
                out.write(("event: " + SseConstant.BUSINESS_ERROR_EVENT + "\ndata: " + errorJson + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(("event: " + SseConstant.DONE_EVENT + "\ndata: {}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException ioException) {
                log.error("Failed to write SSE error response", ioException);
                // 即使写入失败，也表示这是SSE请求
                return true;
            }
        }
        return false;
    }
}