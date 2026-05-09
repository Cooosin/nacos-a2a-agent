package com.nacosa2a.core.model;

import java.util.Map;

public record AgentCard(
        // Agent 名称
        String name,
        // Agent 描述
        String description,
        // Agent 版本
        String version,
        // Agent 对外访问地址
        String endpoint,
        // Agent 标签
        String[] tags,
        // Agent 扩展元数据，由注解中的 key=value 数组转换后写入
        Map<String, String> metadata,
        // Agent 暴露的方法列表
        AgentMethodDefinition[] methods) {
}
