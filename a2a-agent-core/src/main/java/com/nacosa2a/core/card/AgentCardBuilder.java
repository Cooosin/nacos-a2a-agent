package com.nacosa2a.core.card;

import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.core.endpoint.EndpointResolver;
import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.core.model.AgentMethodDefinition;
import com.nacosa2a.core.schema.SchemaResolver;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将带有 A2AAgent 注解的方法映射为注册时使用的 AgentCard。
 */
public class AgentCardBuilder {

    private final SchemaResolver schemaResolver;

    public AgentCardBuilder(SchemaResolver schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    /**
     * 基于单个 A2AAgent 方法构建 AgentCard 与对应的方法定义。
     */
    public AgentCard build(Method method) {
        A2AAgent annotation = method.getAnnotation(A2AAgent.class);
        if (annotation == null) {
            throw new IllegalArgumentException("method must be annotated with @A2AAgent");
        }

        String endpoint = EndpointResolver.resolveAgentEndpoint(annotation.name(), annotation.endpoint());
        Map<String, String> metadata = toMetadataMap(annotation.metadata());
        AgentMethodDefinition methodDefinition = new AgentMethodDefinition(
                method.getName(),
                annotation.description(),
                endpoint,
                annotation.tags(),
                metadata,
                schemaResolver.resolveInputSchema(method),
                schemaResolver.resolveOutputSchema(method)
        );

        return new AgentCard(
                annotation.name(),
                annotation.description(),
                annotation.version(),
                endpoint,
                annotation.tags(),
                metadata,
                new AgentMethodDefinition[]{methodDefinition}
        );
    }

    // 将注解中的 key=value 数组转换为 metadata map，非法值直接忽略。
    private Map<String, String> toMetadataMap(String[] metadataItems) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String metadataItem : metadataItems) {
            if (metadataItem == null || metadataItem.isBlank()) {
                continue;
            }
            int separatorIndex = metadataItem.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= metadataItem.length() - 1) {
                continue;
            }
            String key = metadataItem.substring(0, separatorIndex).trim();
            String value = metadataItem.substring(separatorIndex + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }
}
