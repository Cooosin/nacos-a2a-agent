package com.nacosa2a.core.model;

public record RegistrationPayload(
        // Nacos A2A 中的 agentName
        String serviceName,
        // Nacos 分组
        String groupName,
        // 当前仅用于创建 Nacos A2A Client 时绑定命名空间。
        String namespace,
        // Agent 对外可访问的完整 URL
        String endpoint,
        // Agent 扩展元数据，由注解中的 key=value 数组转换后写入
        java.util.Map<String, String> metadata,
        // 当前服务注册 IP
        String host,
        // 当前服务注册端口
        int port,
        // Agent 注册卡片
        AgentCard agentCard) {
}
