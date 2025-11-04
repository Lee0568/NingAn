package net.thekingofduck.ningan.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理应用程序中的异常，提供一致的错误响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理404错误
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNotFound(NoHandlerFoundException e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.warn("404错误 - 请求路径: {}, 客户端IP: {}", e.getRequestURL(), clientIp);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "success", false,
                    "code", "NOT_FOUND",
                    "message", "请求的资源不存在",
                    "path", e.getRequestURL()
                ));
    }
    
    /**
     * 处理参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.warn("参数异常 - 错误: {}, 客户端IP: {}", e.getMessage(), clientIp);
        
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "code", "INVALID_ARGUMENT",
                    "message", "参数错误: " + e.getMessage()
                ));
    }
    
    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<?> handleNullPointer(NullPointerException e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.error("空指针异常 - 客户端IP: {}", clientIp, e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "code", "NULL_POINTER_ERROR",
                    "message", "系统内部错误，请稍后重试"
                ));
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.error("运行时异常 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "code", "RUNTIME_ERROR",
                    "message", "系统运行时错误: " + e.getMessage()
                ));
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        logger.error("未处理异常 - 客户端IP: {}, 异常类型: {}, 错误: {}", clientIp, e.getClass().getSimpleName(), e.getMessage(), e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "code", "INTERNAL_ERROR",
                    "message", "系统内部错误，请联系管理员",
                    "error_type", e.getClass().getSimpleName()
                ));
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String httpClientIp = request.getHeader("HTTP_CLIENT_IP");
        String httpXForwardedFor = request.getHeader("HTTP_X_FORWARDED_FOR");
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            int index = xForwardedFor.indexOf(',');
            if (index != -1) {
                return xForwardedFor.substring(0, index).trim();
            } else {
                return xForwardedFor.trim();
            }
        }
        
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        if (httpClientIp != null && !httpClientIp.isEmpty() && !"unknown".equalsIgnoreCase(httpClientIp)) {
            return httpClientIp;
        }
        
        if (httpXForwardedFor != null && !httpXForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(httpXForwardedFor)) {
            return httpXForwardedFor;
        }
        
        return request.getRemoteAddr();
    }
}