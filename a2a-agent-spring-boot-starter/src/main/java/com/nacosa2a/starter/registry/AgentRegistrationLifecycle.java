package com.nacosa2a.starter.registry;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.core.model.RegistrationPayload;
import com.nacosa2a.core.registry.AgentRegistrar;
import com.nacosa2a.starter.config.A2ARegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 在应用启动和关闭阶段处理 Agent 注册中心生命周期，默认失败只记录日志不阻塞启动。
 */
public class AgentRegistrationLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistrationLifecycle.class);

    private final AgentMethodRegistry agentMethodRegistry;
    private final ObjectProvider<AgentRegistrar> agentRegistrarProvider;
    private final A2ARegistrationProperties registrationProperties;
    private final Environment environment;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;
    private final List<RegistrationPayload> successfulPayloads = new ArrayList<>();
    private boolean running;

    public AgentRegistrationLifecycle(AgentMethodRegistry agentMethodRegistry,
                                      ObjectProvider<AgentRegistrar> agentRegistrarProvider,
                                      A2ARegistrationProperties registrationProperties,
                                      Environment environment,
                                      NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.agentMethodRegistry = agentMethodRegistry;
        this.agentRegistrarProvider = agentRegistrarProvider;
        this.registrationProperties = registrationProperties;
        this.environment = environment;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
    }

    /**
     * 应用启动后遍历已扫描的 AgentCard 并尝试写入注册中心。
     */
    @Override
    public void start() {
        if (!registrationProperties.isEnabled()) {
            running = true;
            return;
        }

        AgentRegistrar agentRegistrar = agentRegistrarProvider.getIfAvailable();
        if (agentRegistrar == null) {
            running = true;
            return;
        }

        List<AgentCard> agentCards = agentMethodRegistry.getAgentCards();
        for (AgentCard agentCard : agentCards) {
            try {
                RegistrationPayload payload = buildPayload(agentCard);
                agentRegistrar.register(payload);
                successfulPayloads.add(payload);
                log.info("Registered agent {} to Nacos successfully. serviceName={}, group={}, namespace={}, endpoint={}",
                        agentCard.name(),
                        payload.serviceName(),
                        payload.groupName(),
                        payload.namespace(),
                        payload.endpoint());
            } catch (RuntimeException exception) {
                log.warn("Failed to register agent {} to Nacos. Startup will continue.", agentCard.name(), exception);
            }
        }
        running = true;
    }

    /**
     * 应用关闭时只注销启动期成功注册的 Agent，避免重复清理未成功实例。
     */
    @Override
    public void stop() {
        AgentRegistrar agentRegistrar = agentRegistrarProvider.getIfAvailable();
        if (agentRegistrar != null) {
            for (RegistrationPayload payload : successfulPayloads) {
                try {
                    agentRegistrar.deregister(payload);
                } catch (RuntimeException exception) {
                    log.warn("Failed to deregister agent {} from Nacos during shutdown.", payload.agentCard().name(), exception);
                }
            }
            try {
                agentRegistrar.close();
            } catch (RuntimeException exception) {
                log.warn("Failed to close agent registrar during shutdown.", exception);
            }
        }
        successfulPayloads.clear();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    // AgentScope 的 Nacos A2A 发现按 agentName 直接订阅 AgentCard，因此这里使用 agent 自身名称作为注册名。
    private RegistrationPayload buildPayload(AgentCard agentCard) {
        String host = resolveRegistrationHost();
        int port = resolveRegistrationPort();
        return new RegistrationPayload(
                resolveServiceName(agentCard),
                resolveGroupName(),
                resolveNamespace(),
                buildEndpointUrl(agentCard, host, port),
                agentCard.metadata(),
                host,
                port,
                agentCard
        );
    }

    private String resolveServiceName(AgentCard agentCard) {
        String applicationName = environment.getProperty("spring.application.name");
        if (applicationName == null || applicationName.isBlank()) {
            throw new IllegalStateException("spring.application.name must not be blank for agent registration");
        }
        return agentCard.name();
    }

    private String resolveGroupName() {
        String explicitGroupName = registrationProperties.getGroupName();
        if (explicitGroupName != null && !explicitGroupName.isBlank()) {
            return explicitGroupName;
        }
        String discoveryGroupName = environment.getProperty("spring.cloud.nacos.discovery.group");
        if (discoveryGroupName != null && !discoveryGroupName.isBlank()) {
            return discoveryGroupName;
        }
        return "DEFAULT_GROUP";
    }

    private String resolveNamespace() {
        String explicitNamespace = registrationProperties.getNamespace();
        if (explicitNamespace != null && !explicitNamespace.isBlank()) {
            return explicitNamespace;
        }
        String discoveryNamespace = environment.getProperty("spring.cloud.nacos.discovery.namespace");
        if (discoveryNamespace != null && !discoveryNamespace.isBlank()) {
            return discoveryNamespace;
        }
        return "public";
    }

    private String resolveRegistrationHost() {
        String explicitHost = registrationProperties.getHost();
        if (StringUtils.hasText(explicitHost)) {
            return explicitHost;
        }
        if (nacosDiscoveryProperties != null && StringUtils.hasText(nacosDiscoveryProperties.getIp())) {
            return nacosDiscoveryProperties.getIp();
        }
        String discoveryHost = environment.getProperty("spring.cloud.nacos.discovery.ip");
        if (StringUtils.hasText(discoveryHost)) {
            return discoveryHost;
        }
        throw new IllegalStateException("failed to resolve registration host from a2a.registration.host or NacosDiscoveryProperties or spring.cloud.nacos.discovery.ip");
    }

    private int resolveRegistrationPort() {
        Integer explicitPort = registrationProperties.getPort();
        if (explicitPort != null && explicitPort > 0) {
            return explicitPort;
        }
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        if (serverPort != null && serverPort > 0) {
            return serverPort;
        }
        throw new IllegalStateException("failed to resolve registration port from a2a.registration.port or server.port");
    }

    // 统一生成 AgentCard.url，供 AgentScope 从 Nacos 拿到卡片后直接回调当前服务。
    private String buildEndpointUrl(AgentCard agentCard, String host, int port) {
        String scheme = registrationProperties.isSupportTls() ? "https" : "http";
        return scheme + "://" + host + ":" + port + agentCard.endpoint();
    }
}
