package com.yuluo.tourguide.common;


import com.yuluo.tourguide.exception.ErrorCode;

/**
 * 快速构造响应结果的工具类
 */
public class ResultUtils {

    /**
     * 成功响应
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 失败响应，取错误码默认信息
     */
    public static BaseResponse<?> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 失败响应，自定义码与信息
     */
    public static BaseResponse<?> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 失败响应，取错误码、覆写信息
     */
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}