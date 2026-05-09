package com.nacosa2a.starter.registry;

import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.core.endpoint.EndpointResolver;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 维护 A2A 协议入口到目标方法的运行时映射，并负责按请求数据调用目标方法。
 */
public class AgentRuntimeRegistry {

    private final ObjectMapper objectMapper;
    private final ParameterNameDiscoverer parameterNameDiscoverer;
    private final Map<String, RegisteredAgentHandler> handlersByName = new LinkedHashMap<>();

    public AgentRuntimeRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    }

    /**
     * 注册单个 A2AAgent 方法对应的运行时处理器，并按 Task 4 规则校验 name 与 endpoint 全局唯一。
     */
    public void register(Method method, Object bean) {
        A2AAgent annotation = method.getAnnotation(A2AAgent.class);
        if (annotation == null) {
            throw new IllegalArgumentException("method must be annotated with @A2AAgent");
        }

        String agentName = annotation.name();
        String endpoint = EndpointResolver.resolveAgentEndpoint(agentName, annotation.endpoint());
        RegisteredAgentHandler handler = new RegisteredAgentHandler(bean, method, agentName, endpoint, objectMapper, parameterNameDiscoverer);
        validateUniqueRegistration(agentName, endpoint);
        handlersByName.put(agentName, handler);
    }

    /**
     * 按 agent name 调用已注册的方法，统一从 /a2a/{agentName} 的 JSON body 组装业务入参。
     */
    public Object invokeByAgentName(String agentName, Object requestBody) {
        RegisteredAgentHandler handler = handlersByName.get(agentName);
        if (handler == null) {
            throw new IllegalStateException("agent not found: " + agentName);
        }
        return handler.invoke(requestBody);
    }

    /**
     * 返回当前 agent name 是否已完成运行时注册。
     */
    public boolean containsAgentName(String agentName) {
        return handlersByName.containsKey(agentName);
    }

    // Task 5 统一通过 /a2a/{agentName} 协议入口路由，这里只需要校验 endpoint 唯一性，不再额外保存 endpoint -> handler 状态。
    private void validateUniqueRegistration(String agentName, String endpoint) {
        if (handlersByName.containsKey(agentName)) {
            throw new IllegalStateException("duplicate agent name: " + agentName);
        }
        for (RegisteredAgentHandler registeredHandler : handlersByName.values()) {
            if (Objects.equals(registeredHandler.endpoint(), endpoint)) {
                throw new IllegalStateException("duplicate agent endpoint: " + endpoint);
            }
        }
    }

    private record RegisteredAgentHandler(Object bean,
                                          Method method,
                                          String agentName,
                                          String endpoint,
                                          ObjectMapper objectMapper,
                                          ParameterNameDiscoverer parameterNameDiscoverer) {

        Object invoke(Object requestBody) {
            try {
                Object[] arguments = resolveArguments(requestBody);
                return method.invoke(bean, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("failed to access agent method: " + agentName, exception);
            } catch (InvocationTargetException exception) {
                Throwable targetException = exception.getTargetException();
                if (targetException instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("agent method invocation failed: " + agentName, targetException);
            }
        }

        // 协议入口统一接收 JSON body，单参数方法直接转换，多参数方法按注解或参数名从统一 body 中拆分字段。
        private Object[] resolveArguments(Object requestBody) {
            Parameter[] parameters = method.getParameters();
            if (parameters.length == 0) {
                return new Object[0];
            }
            if (parameters.length == 1 && canBindWholeBody(parameters[0])) {
                Object convertedBody = convertValue(requestBody, parameters[0].getType());
                return new Object[]{convertedBody};
            }
            Map<String, Object> requestFields = convertRequestBodyToMap(requestBody);
            Object[] arguments = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                arguments[index] = resolveArgument(parameters[index], requestFields);
            }
            return arguments;
        }

        private boolean canBindWholeBody(Parameter parameter) {
            return parameter.isAnnotationPresent(RequestBody.class) || method.getParameterCount() == 1;
        }

        // @RequestBody 参数只消费与自身字段匹配的内容，其余参数从顶层字段中按注解名称或参数名取值。
        private Object resolveArgument(Parameter parameter, Map<String, Object> requestFields) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                Object requestBodyValue = extractBodyValue(requestFields, parameter.getType());
                return convertValue(requestBodyValue, parameter.getType());
            }
            String fieldName = resolveFieldName(parameter);
            Object fieldValue = requestFields.get(fieldName);
            return convertValue(fieldValue, parameter.getType());
        }

        private String resolveFieldName(Parameter parameter) {
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            if (pathVariable != null && !pathVariable.value().isBlank()) {
                return pathVariable.value();
            }

            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null && !requestParam.value().isBlank()) {
                return requestParam.value();
            }

            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            int parameterIndex = resolveParameterIndex(parameter);
            if (parameterNames != null && parameterIndex >= 0 && parameterIndex < parameterNames.length) {
                String parameterName = parameterNames[parameterIndex];
                if (parameterName != null && !parameterName.isBlank()) {
                    return parameterName;
                }
            }
            return parameter.getName();
        }

        private int resolveParameterIndex(Parameter parameter) {
            Parameter[] parameters = method.getParameters();
            for (int index = 0; index < parameters.length; index++) {
                if (parameters[index] == parameter) {
                    return index;
                }
            }
            return -1;
        }

        private Map<String, Object> convertRequestBodyToMap(Object requestBody) {
            if (requestBody == null) {
                return Map.of();
            }
            if (requestBody instanceof Map<?, ?> requestBodyMap) {
                Map<String, Object> convertedMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : requestBodyMap.entrySet()) {
                    convertedMap.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return convertedMap;
            }
            return objectMapper.convertValue(requestBody, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        }

        private Object extractBodyValue(Map<String, Object> requestFields, Class<?> targetType) {
            if (requestFields.isEmpty()) {
                return null;
            }
            if (Map.class.isAssignableFrom(targetType)) {
                return requestFields;
            }
            Map<String, Object> bodyFields = filterBodyFields(requestFields, targetType);
            if (!bodyFields.isEmpty()) {
                return bodyFields;
            }
            return requestFields;
        }

        private Map<String, Object> filterBodyFields(Map<String, Object> requestFields, Class<?> targetType) {
            Map<String, Object> bodyFields = new LinkedHashMap<>();
            if (targetType.isRecord()) {
                for (java.lang.reflect.RecordComponent recordComponent : targetType.getRecordComponents()) {
                    copyFieldIfPresent(requestFields, bodyFields, recordComponent.getName());
                }
                return bodyFields;
            }

            JavaType javaType = objectMapper.constructType(targetType);
            for (BeanPropertyDefinition property : objectMapper.getSerializationConfig().introspect(javaType).findProperties()) {
                copyFieldIfPresent(requestFields, bodyFields, property.getName());
            }
            return bodyFields;
        }

        private void copyFieldIfPresent(Map<String, Object> requestFields, Map<String, Object> bodyFields, String fieldName) {
            if (requestFields.containsKey(fieldName)) {
                bodyFields.put(fieldName, requestFields.get(fieldName));
            }
        }

        // 协议入口统一接收 JSON body，这里按目标方法参数类型转换为真正的业务入参对象。
        private Object convertValue(Object sourceValue, Class<?> targetType) {
            if (sourceValue == null) {
                return null;
            }
            if (targetType.isInstance(sourceValue)) {
                return sourceValue;
            }
            return objectMapper.convertValue(sourceValue, targetType);
        }
    }
}
