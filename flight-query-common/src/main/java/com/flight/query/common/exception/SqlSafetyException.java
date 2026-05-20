package com.flight.query.common.exception;

import com.flight.query.common.result.ErrorCode;

/**
 * SQL安全校验异常
 * <p>
 * 当生成的SQL包含非法操作（DELETE/DROP/UPDATE等）时抛出，
 * 该异常不可重试，直接终止查询流程。
 */
public class SqlSafetyException extends BusinessException {

    public SqlSafetyException(String message) {
        super(ErrorCode.SQL_SAFETY_VIOLATION, message);
    }
}
