package com.example.aiteacher.common;

import lombok.Getter;

/**
 * 业务异常：全局由 GlobalExceptionHandler 统一转为 Result 返回
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ResultCode.SERVER_ERROR.getCode(), message);
    }

    public BizException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
