package com.nacosa2a.core.schema;

import com.nacosa2a.core.model.ParameterDescriptor;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 A2A 方法的输入输出 Schema，并在输入字段上补充参数来源元数据。
 */
public class SchemaResolver {

    public static final String PARAMETER_SOURCE_EXTENSION = "x-parameter-source";

    private static final String SOURCE_PATH = "path";
    private static final String SOURCE_QUERY = "query";
    private static final String SOURCE_BODY = "body";

    private final OpenApiSchemaBuilder openApiSchemaBuilder;
    private final ParameterNameDiscoverer parameterNameDiscoverer;

    public SchemaResolver(OpenApiSchemaBuilder openApiSchemaBuilder) {
        this.openApiSchemaBuilder = openApiSchemaBuilder;
        this.parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    }

    /**
     * 将方法入参合并为一个 object schema，并在字段级别保留来源信息。
     */
    public Schema<?> resolveInputSchema(Method method) {
        ObjectSchema inputSchema = new ObjectSchema();
        Map<String, Schema> properties = new LinkedHashMap<>();

        List<ParameterDescriptor> parameterDescriptors = describeParameters(method);
        for (ParameterDescriptor parameterDescriptor : parameterDescriptors) {
            appendParameterSchema(properties, parameterDescriptor);
        }

        inputSchema.setProperties(properties);
        return inputSchema;
    }

    /**
     * 按方法返回类型构建输出 schema。
     */
    public Schema<?> resolveOutputSchema(Method method) {
        return openApiSchemaBuilder.buildSchema(method.getGenericReturnType());
    }

    // 按 Spring MVC 参数来源识别字段名称和来源，供统一输入 schema 组装使用。
    private List<ParameterDescriptor> describeParameters(Method method) {
        List<ParameterDescriptor> descriptors = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            String parameterName = resolveParameterName(parameter, parameterNames, index);
            String source = resolveParameterSource(parameter);
            descriptors.add(new ParameterDescriptor(parameter, parameterName, source));
        }
        return descriptors;
    }

    // body 参数按对象字段展开，其余参数直接作为顶层字段写入统一 schema。
    private void appendParameterSchema(Map<String, Schema> properties, ParameterDescriptor parameterDescriptor) {
        Schema<?> schema = openApiSchemaBuilder.buildSchema(parameterDescriptor.parameter().getParameterizedType());
        if (SOURCE_BODY.equals(parameterDescriptor.source()) && isExpandableObject(schema)) {
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                Schema<?> fieldSchema = cloneSchema(entry.getValue());
                fieldSchema.addExtension(PARAMETER_SOURCE_EXTENSION, SOURCE_BODY);
                properties.put(entry.getKey(), fieldSchema);
            }
            return;
        }

        schema.addExtension(PARAMETER_SOURCE_EXTENSION, parameterDescriptor.source());
        properties.put(parameterDescriptor.fieldName(), schema);
    }

    private boolean isExpandableObject(Schema<?> schema) {
        return schema != null && "object".equals(schema.getType()) && schema.getProperties() != null && !schema.getProperties().isEmpty();
    }

    private String resolveParameterName(Parameter parameter, String[] parameterNames, int index) {
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isBlank()) {
            return pathVariable.value();
        }

        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isBlank()) {
            return requestParam.value();
        }

        if (parameterNames != null && index < parameterNames.length && parameterNames[index] != null && !parameterNames[index].isBlank()) {
            return parameterNames[index];
        }
        return parameter.getName();
    }

    private String resolveParameterSource(Parameter parameter) {
        if (parameter.isAnnotationPresent(PathVariable.class)) {
            return SOURCE_PATH;
        }
        if (parameter.isAnnotationPresent(RequestParam.class)) {
            return SOURCE_QUERY;
        }
        if (parameter.isAnnotationPresent(RequestBody.class)) {
            return SOURCE_BODY;
        }
        return SOURCE_BODY;
    }

    private Schema<?> cloneSchema(Schema<?> schema) {
        return copySchema(schema);
    }

    private Schema<?> copySchema(Schema<?> schema) {
        Schema<?> copy = new Schema<>();
        copy.setType(schema.getType());
        copy.setFormat(schema.getFormat());
        copy.setDescription(schema.getDescription());
        copy.setNullable(schema.getNullable());
        if (schema.getRequired() != null) {
            copy.setRequired(new java.util.ArrayList<>(schema.getRequired()));
        }
        if (schema.getEnum() != null) {
            copy.setEnum(new java.util.ArrayList(schema.getEnum()));
        }
        copy.set$ref(schema.get$ref());
        if (schema.getProperties() != null) {
            Map<String, Schema> childProperties = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                childProperties.put(entry.getKey(), copySchema(entry.getValue()));
            }
            copy.setProperties(childProperties);
            if (copy.getType() == null) {
                copy.setType("object");
            }
        }
        if (schema.getItems() != null) {
            copy.setItems(copySchema(schema.getItems()));
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
            copy.setAdditionalProperties(copySchema(additionalSchema));
        } else {
            copy.setAdditionalProperties(schema.getAdditionalProperties());
        }
        return copy;
    }
}
