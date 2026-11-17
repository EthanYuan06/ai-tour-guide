package com.yuluo.tourguide.common;

import lombok.Data;

/**
 * 请求封装类
 */
@Data
public class PageRequest {

    private int pageNum = 1;

    private int pageSize = 10;

    private String sortField;

    private String sortOrder = "descend";
}