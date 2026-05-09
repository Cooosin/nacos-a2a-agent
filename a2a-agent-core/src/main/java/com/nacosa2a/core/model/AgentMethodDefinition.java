package com.nacosa2a.core.model;

import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;

public record AgentMethodDefinition(
        // 方法名称
        String name,
        // 方法说明
        String description,
        // 方法访问地址
        String endpoint,
        // 方法标签
        String[] tags,
        // 方法扩展元数据，由注解中的 key=value 数组转换后写入
        Map<String, String> metadata,
        // 方法入参 Schema，多个参数会合并为一个 object
        Schema<?> inputSchema,
        // 方法出参 Schema
        Schema<?> outputSchema) {
}
