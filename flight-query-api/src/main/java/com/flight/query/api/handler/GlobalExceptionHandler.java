package com.flight.query.api.handler;

import com.flight.query.common.exception.BusinessException;
import com.flight.query.common.exception.SqlSafetyException;
import com.flight.query.common.result.ErrorCode;
import com.flight.query.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 统一捕获异常并转换为标准化的 Result 返回给前端，
 * 避免异常堆栈信息直接暴露给客户端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * SQL安全异常 - 最高优先级
     */
    @ExceptionHandler(SqlSafetyException.class)
    public Result<Void> handleSqlSafetyException(SqlSafetyException e) {
        log.error("[安全拦截] SQL安全校验失败: {}", e.getMessage());
        return Result.fail(ErrorCode.SQL_SAFETY_VIOLATION, "查询包含非法操作，已被拦截");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[参数异常] {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 兜底 - 未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常] 未捕获的异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误，请稍后重试");
    }
}
