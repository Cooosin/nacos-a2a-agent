package com.nacosa2a.starter.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.nacosa2a.core.card.AgentCardBuilder;
import com.nacosa2a.core.model.RegistrationPayload;
import com.nacosa2a.core.registry.AgentRegistrar;
import com.nacosa2a.core.registry.NacosAgentRegistrar;
import com.nacosa2a.core.schema.OpenApiSchemaBuilder;
import com.nacosa2a.core.schema.SchemaResolver;
import com.nacosa2a.starter.controller.A2AProtocolController;
import com.nacosa2a.starter.controller.AgentCardController;
import com.nacosa2a.starter.registry.AgentMethodRegistry;
import com.nacosa2a.starter.registry.AgentRegistrationLifecycle;
import com.nacosa2a.starter.registry.AgentRuntimeRegistry;
import com.nacosa2a.starter.scanner.A2AAgentScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.function.Supplier;

/**
 * 自动装配 A2A 协议入口、方法扫描和运行时注册能力。
 */
@AutoConfiguration
@EnableConfigurationProperties(A2ARegistrationProperties.class)
public class A2AAgentAutoConfiguration {

    @Bean
    public OpenApiSchemaBuilder openApiSchemaBuilder() {
        return new OpenApiSchemaBuilder();
    }

    @Bean
    public SchemaResolver schemaResolver(OpenApiSchemaBuilder openApiSchemaBuilder) {
        return new SchemaResolver(openApiSchemaBuilder);
    }

    @Bean
    public AgentCardBuilder agentCardBuilder(SchemaResolver schemaResolver) {
        return new AgentCardBuilder(schemaResolver);
    }

    @Bean
    public AgentMethodRegistry agentMethodRegistry(AgentCardBuilder agentCardBuilder) {
        return new AgentMethodRegistry(agentCardBuilder);
    }

    @Bean
    public AgentRuntimeRegistry agentRuntimeRegistry(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new AgentRuntimeRegistry(objectMapper);
    }

    @Bean
    public A2AAgentScanner a2AAgentScanner(AgentMethodRegistry agentMethodRegistry, AgentRuntimeRegistry agentRuntimeRegistry) {
        return new A2AAgentScanner(agentMethodRegistry, agentRuntimeRegistry);
    }

    @Bean
    public SmartInitializingSingleton a2aAgentBootstrap(A2AAgentScanner a2AAgentScanner,
                                                        RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return () -> a2AAgentScanner.scan(requestMappingHandlerMapping.getHandlerMethods().values());
    }

    @Bean
    @ConditionalOnMissingBean(AgentRegistrar.class)
    public AgentRegistrar agentRegistrar(A2ARegistrationProperties registrationProperties,
                                         Environment environment,
                                         ObjectProvider<NacosDiscoveryProperties> nacosDiscoveryPropertiesProvider) {
        Supplier<AgentRegistrar> registrarSupplier = () -> createDefaultAgentRegistrar(registrationProperties, environment, nacosDiscoveryPropertiesProvider.getIfAvailable());
        return new DeferredAgentRegistrar(registrarSupplier);
    }

    @Bean
    public AgentRegistrationLifecycle agentRegistrationLifecycle(AgentMethodRegistry agentMethodRegistry,
                                                                ObjectProvider<AgentRegistrar> agentRegistrarProvider,
                                                                A2ARegistrationProperties registrationProperties,
                                                                Environment environment,
                                                                ObjectProvider<NacosDiscoveryProperties> nacosDiscoveryPropertiesProvider) {
        return new AgentRegistrationLifecycle(agentMethodRegistry, agentRegistrarProvider, registrationProperties, environment,
                nacosDiscoveryPropertiesProvider.getIfAvailable());
    }

    @Bean
    public A2AProtocolController a2AProtocolController(AgentRuntimeRegistry agentRuntimeRegistry) {
        return new A2AProtocolController(agentRuntimeRegistry);
    }

    @Bean
    public AgentCardController agentCardController(AgentMethodRegistry agentMethodRegistry) {
        return new AgentCardController(agentMethodRegistry);
    }

    // 在真正发起注册前解析最终生效的 Nacos 连接信息和实例地址，异常交给生命周期统一记录日志。
    private AgentRegistrar createDefaultAgentRegistrar(A2ARegistrationProperties registrationProperties,
                                                       Environment environment,
                                                       NacosDiscoveryProperties nacosDiscoveryProperties) {
        String serverAddr = firstNonBlank(
                registrationProperties.getServerAddr(),
                environment.getProperty("spring.cloud.nacos.discovery.server-addr"),
                "127.0.0.1:8848"
        );
        String namespace = firstNonBlank(
                registrationProperties.getNamespace(),
                environment.getProperty("spring.cloud.nacos.discovery.namespace"),
                "public"
        );
        String username = firstNonBlank(
                registrationProperties.getUsername(),
                environment.getProperty("spring.cloud.nacos.discovery.username")
        );
        String password = firstNonBlank(
                registrationProperties.getPassword(),
                environment.getProperty("spring.cloud.nacos.discovery.password")
        );
        String host = resolveRegistrationHost(registrationProperties, environment, nacosDiscoveryProperties);
        Integer port = resolveRegistrationPort(registrationProperties, environment);
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("failed to resolve registration host from a2a.registration.host or NacosDiscoveryProperties or spring.cloud.nacos.discovery.ip");
        }
        if (port == null || port <= 0) {
            throw new IllegalStateException("failed to resolve registration port from a2a.registration.port or server.port");
        }
        return new NacosAgentRegistrar(
                serverAddr,
                namespace,
                username,
                password,
                registrationProperties.isEnabledRegisterEndpoint(),
                registrationProperties.getPreferredTransport(),
                registrationProperties.isSupportTls(),
                registrationProperties.isRegisterAsLatest()
        );
    }

    private String resolveRegistrationHost(A2ARegistrationProperties registrationProperties,
                                           Environment environment,
                                           NacosDiscoveryProperties nacosDiscoveryProperties) {
        return firstNonBlank(
                registrationProperties.getHost(),
                resolveNacosDiscoveryHost(nacosDiscoveryProperties),
                environment.getProperty("spring.cloud.nacos.discovery.ip")
        );
    }

    private String resolveNacosDiscoveryHost(NacosDiscoveryProperties nacosDiscoveryProperties) {
        if (nacosDiscoveryProperties == null) {
            return null;
        }
        return nacosDiscoveryProperties.getIp();
    }

    private Integer resolveRegistrationPort(A2ARegistrationProperties registrationProperties, Environment environment) {
        Integer explicitPort = registrationProperties.getPort();
        if (explicitPort != null && explicitPort > 0) {
            return explicitPort;
        }
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        if (serverPort != null && serverPort > 0) {
            return serverPort;
        }
        return null;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // 默认 Nacos 注册器延迟到生命周期阶段再创建真实实现，避免 Bean 创建提前阻塞应用启动。
    private static final class DeferredAgentRegistrar implements AgentRegistrar {

        private final Supplier<AgentRegistrar> registrarSupplier;
        private AgentRegistrar delegate;

        private DeferredAgentRegistrar(Supplier<AgentRegistrar> registrarSupplier) {
            this.registrarSupplier = registrarSupplier;
        }

        @Override
        public void register(RegistrationPayload payload) {
            getDelegate().register(payload);
        }

        @Override
        public void deregister(RegistrationPayload payload) {
            getDelegate().deregister(payload);
        }

        @Override
        public void close() {
            if (delegate != null) {
                delegate.close();
            }
        }

        private AgentRegistrar getDelegate() {
            if (delegate == null) {
                delegate = registrarSupplier.get();
            }
            return delegate;
        }
    }
}
