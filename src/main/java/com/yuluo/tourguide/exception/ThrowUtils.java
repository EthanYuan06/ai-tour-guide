package com.yuluo.tourguide.exception;

/**
 * 抛异常工具类
 * 简化抛异常的代码，类似断言类
 */
public class ThrowUtils {

    /**
     * 条件成立则抛出给定异常，以下重载均基于它
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立则按错误码抛业务异常
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 条件成立则按错误码抛业务异常，并覆写错误信息
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}
