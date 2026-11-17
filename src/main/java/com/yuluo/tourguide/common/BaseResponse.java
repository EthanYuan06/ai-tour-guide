package com.yuluo.tourguide.common;


import com.yuluo.tourguide.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应类，code=0 表示成功，data 为业务载荷，message 为提示信息
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
