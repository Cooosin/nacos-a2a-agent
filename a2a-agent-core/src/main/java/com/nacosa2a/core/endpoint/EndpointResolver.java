package com.nacosa2a.core.endpoint;

public final class EndpointResolver {

    private static final String DEFAULT_ENDPOINT_TEMPLATE = "/a2a/%s";

    private EndpointResolver() {
    }

    /**
     * 解析 Agent 对外访问地址，优先使用自定义 endpoint，未配置时按名称生成默认地址。
     */
    public static String resolveAgentEndpoint(String name, String endpoint) {
        String normalizedCustomEndpoint = normalizeCustomEndpoint(endpoint);
        if (normalizedCustomEndpoint != null) {
            return normalizedCustomEndpoint;
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        return String.format(DEFAULT_ENDPOINT_TEMPLATE, name.trim());
    }

    // 自定义 endpoint 统一输出为 /path 形式，空白值按未配置处理。
    private static String normalizeCustomEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }

        String normalizedEndpoint = endpoint.trim();
        if (!normalizedEndpoint.startsWith("/")) {
            normalizedEndpoint = "/" + normalizedEndpoint;
        }
        while (normalizedEndpoint.length() > 1 && normalizedEndpoint.endsWith("/")) {
            normalizedEndpoint = normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1);
        }
        return normalizedEndpoint;
    }
}
