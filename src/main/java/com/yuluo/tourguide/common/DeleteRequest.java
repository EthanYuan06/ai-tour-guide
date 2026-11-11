package com.yuluo.tourguide.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除请求包装类
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 待删除记录 id，用 String 承载以规避前端 JS Long 精度丢失
     */
    private String id;

    private static final long serialVersionUID = 1L;
}