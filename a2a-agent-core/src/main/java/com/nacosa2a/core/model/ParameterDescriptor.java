package com.nacosa2a.core.model;

import java.lang.reflect.Parameter;

public record ParameterDescriptor(
        // 反射参数对象，用于后续解析类型与注解
        Parameter parameter,
        // 输入字段名
        String fieldName,
        // 参数来源，区分 path/query/body 等来源
        String source) {
}
