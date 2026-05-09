package com.nacosa2a.starter.scanner;

import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.starter.registry.AgentMethodRegistry;
import com.nacosa2a.starter.registry.AgentRuntimeRegistry;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * 扫描 Spring MVC HandlerMethod，只注册带有 A2AAgent 注解的方法。
 */
public class A2AAgentScanner {

    private final Consumer<Method> methodRegistrar;
    private final AgentRuntimeRegistry agentRuntimeRegistry;

    public A2AAgentScanner(AgentMethodRegistry agentMethodRegistry, AgentRuntimeRegistry agentRuntimeRegistry) {
        this(agentMethodRegistry::register, agentRuntimeRegistry);
    }

    A2AAgentScanner(Consumer<Method> methodRegistrar) {
        this(methodRegistrar, null);
    }

    A2AAgentScanner(Consumer<Method> methodRegistrar, AgentRuntimeRegistry agentRuntimeRegistry) {
        this.methodRegistrar = methodRegistrar;
        this.agentRuntimeRegistry = agentRuntimeRegistry;
    }

    /**
     * 遍历当前应用中的 HandlerMethod，筛选并注册 A2AAgent 方法。
     */
    public void scan(Iterable<HandlerMethod> handlerMethods) {
        for (HandlerMethod handlerMethod : handlerMethods) {
            Method method = handlerMethod.getMethod();
            if (method.isAnnotationPresent(A2AAgent.class)) {
                methodRegistrar.accept(method);
                registerRuntimeHandler(handlerMethod, method);
            }
        }
    }

    // 启动扫描时同步写入运行时路由，供生成的 A2A 协议入口按 agent name 调用目标方法。
    private void registerRuntimeHandler(HandlerMethod handlerMethod, Method method) {
        if (agentRuntimeRegistry == null) {
            return;
        }
        HandlerMethod resolvedHandlerMethod = handlerMethod.createWithResolvedBean();
        agentRuntimeRegistry.register(method, resolvedHandlerMethod.getBean());
    }
}
