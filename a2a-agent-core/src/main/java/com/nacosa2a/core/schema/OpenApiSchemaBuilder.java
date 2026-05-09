package com.nacosa2a.core.schema;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.core.ResolvableType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 springdoc 与 swagger model converter 构建 Java 类型对应的 OpenAPI Schema。
 */
public class OpenApiSchemaBuilder {

    /**
     * 将 Java 类型转换为 OpenAPI Schema，复杂对象会递归补齐字段定义。
     */
    public Schema<?> buildSchema(Type type) {
        Schema<?> resolvedSchema = resolveWithModelConverters(type);
        if (resolvedSchema != null) {
            return resolvedSchema;
        }
        if (type instanceof Class<?> clazz) {
            return buildSchema(clazz);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return buildSchema(parameterizedType.getRawType());
        }

        ResolvableType resolvableType = ResolvableType.forType(type);
        Class<?> resolvedClass = resolvableType.resolve();
        if (resolvedClass == null) {
            return new ObjectSchema();
        }
        return buildSchema(resolvedClass);
    }

    /**
     * 将 Java Class 转换为 OpenAPI Schema。
     */
    public Schema<?> buildSchema(Class<?> type) {
        if (String.class.equals(type) || Character.class.equals(type) || char.class.equals(type)) {
            return new StringSchema();
        }
        if (Integer.class.equals(type) || int.class.equals(type)
                || Long.class.equals(type) || long.class.equals(type)
                || Short.class.equals(type) || short.class.equals(type)
                || Byte.class.equals(type) || byte.class.equals(type)) {
            return new IntegerSchema();
        }
        if (Double.class.equals(type) || double.class.equals(type)
                || Float.class.equals(type) || float.class.equals(type)) {
            return new NumberSchema();
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            Schema<Boolean> schema = new Schema<>();
            schema.setType("boolean");
            return schema;
        }
        if (Void.class.equals(type) || void.class.equals(type)) {
            return new ObjectSchema();
        }
        if (type.isEnum()) {
            StringSchema schema = new StringSchema();
            Object[] enumConstants = type.getEnumConstants();
            for (Object enumConstant : enumConstants) {
                schema.addEnumItemObject(String.valueOf(enumConstant));
            }
            return schema;
        }

        Schema<?> resolvedSchema = resolveWithModelConverters(type);
        if (resolvedSchema != null) {
            return resolvedSchema;
        }
        return new ObjectSchema();
    }

    // 优先复用 swagger model converter 产出的对象结构，兼容 record、普通 JavaBean 与泛型类型。
    private Schema<?> resolveWithModelConverters(Type type) {
        AnnotatedType annotatedType = new AnnotatedType(type).resolveAsRef(false);
        ResolvedSchema resolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(annotatedType);
        if (resolvedSchema == null) {
            return null;
        }

        Map<String, Schema> referencedSchemas = resolvedSchema.referencedSchemas;
        if (referencedSchemas == null || referencedSchemas.isEmpty()) {
            return normalizeSchema(resolvedSchema.schema);
        }
        return normalizeSchema(resolveReferencedSchema(resolvedSchema.schema, referencedSchemas));
    }

    // 将 schema 中所有 $ref 递归展开为当前字段可直接使用的结构。
    private Schema<?> resolveReferencedSchema(Schema<?> schema, Map<String, Schema> referencedSchemas) {
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null) {
            String referenceName = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
            Schema<?> referencedSchema = referencedSchemas.get(referenceName);
            if (referencedSchema == null) {
                return schema;
            }
            return resolveReferencedSchema(cloneSchema(referencedSchema), referencedSchemas);
        }

        Schema<?> copy = cloneSchema(schema);
        if (copy.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : copy.getProperties().entrySet()) {
                entry.setValue(resolveReferencedSchema(entry.getValue(), referencedSchemas));
            }
        }
        if (copy.getItems() != null) {
            copy.setItems(resolveReferencedSchema(copy.getItems(), referencedSchemas));
        }
        if (copy.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
            copy.setAdditionalProperties(resolveReferencedSchema(additionalSchema, referencedSchemas));
        }
        return copy;
    }

    // 统一补齐 object 类型，避免测试和后续构建读取时出现空 type。
    private Schema<?> normalizeSchema(Schema<?> schema) {
        if (schema == null) {
            return new ObjectSchema();
        }
        if (schema.get$ref() != null) {
            Schema<Object> referenceSchema = new Schema<>();
            referenceSchema.set$ref(schema.get$ref());
            return referenceSchema;
        }
        if (schema.getType() == null && schema.getProperties() != null) {
            schema.setType("object");
        }
        return cloneSchema(schema);
    }

    private Schema<?> cloneSchema(Schema<?> schema) {
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
            Map<String, Schema> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), cloneSchema(entry.getValue()));
            }
            copy.setProperties(properties);
            if (copy.getType() == null) {
                copy.setType("object");
            }
        }
        if (schema.getItems() != null) {
            copy.setItems(cloneSchema(schema.getItems()));
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
            copy.setAdditionalProperties(cloneSchema(additionalSchema));
        } else {
            copy.setAdditionalProperties(schema.getAdditionalProperties());
        }
        return copy;
    }
}
