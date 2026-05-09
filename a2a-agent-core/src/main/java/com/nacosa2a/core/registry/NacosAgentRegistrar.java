package com.nacosa2a.core.registry;

import com.alibaba.nacos.api.ai.A2aService;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.exception.NacosException;
import com.nacosa2a.core.model.AgentMethodDefinition;
import com.nacosa2a.core.model.RegistrationPayload;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 基于 Nacos A2A API 实现 AgentCard 与 AgentEndpoint 的注册和注销。
 */
public class NacosAgentRegistrar implements AgentRegistrar {

    private static final String NACOS_SERVER_ADDR = "serverAddr";
    private static final String NACOS_NAMESPACE = "namespace";
    private static final String NACOS_USERNAME = "username";
    private static final String NACOS_PASSWORD = "password";

    private final A2aService a2aService;
    private final AiService aiService;
    private final boolean enabledRegisterEndpoint;
    private final String preferredTransport;
    private final boolean supportTls;
    private final boolean registerAsLatest;

    public NacosAgentRegistrar(String serverAddr,
                               String namespace,
                               String username,
                               String password,
                               boolean enabledRegisterEndpoint,
                               String preferredTransport,
                               boolean supportTls,
                               boolean registerAsLatest) {
        this(createAiService(serverAddr, namespace, username, password),
                enabledRegisterEndpoint,
                preferredTransport,
                supportTls,
                registerAsLatest);
    }

    NacosAgentRegistrar(AiService aiService,
                        boolean enabledRegisterEndpoint,
                        String preferredTransport,
                        boolean supportTls,
                        boolean registerAsLatest) {
        this.aiService = aiService;
        this.a2aService = aiService;
        this.enabledRegisterEndpoint = enabledRegisterEndpoint;
        this.preferredTransport = normalizePreferredTransport(preferredTransport);
        this.supportTls = supportTls;
        this.registerAsLatest = registerAsLatest;
    }

    /**
     * 发布完整 AgentCard，并按配置把 AgentEndpoint 注册到 Nacos A2A 注册中心。
     */
    @Override
    public void register(RegistrationPayload payload) {
        try {
            a2aService.releaseAgentCard(buildAgentCard(payload), AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE, registerAsLatest);
            if (enabledRegisterEndpoint) {
                a2aService.registerAgentEndpoint(payload.serviceName(), buildAgentEndpoint(payload));
            }
        } catch (NacosException exception) {
            throw new IllegalStateException("failed to register agent to nacos a2a: " + payload.serviceName(), exception);
        }
    }

    /**
     * 注销当前 Agent 对应的 Endpoint。
     */
    @Override
    public void deregister(RegistrationPayload payload) {
        if (!enabledRegisterEndpoint) {
            return;
        }
        try {
            a2aService.deregisterAgentEndpoint(payload.serviceName(), buildAgentEndpoint(payload));
        } catch (NacosException exception) {
            throw new IllegalStateException("failed to deregister agent endpoint from nacos a2a: " + payload.serviceName(), exception);
        }
    }

    /**
     * 关闭当前注册器持有的 A2A 连接。
     */
    @Override
    public void close() {
        try {
            aiService.shutdown();
        } catch (NacosException exception) {
            throw new IllegalStateException("failed to shutdown nacos a2a service", exception);
        }
    }

    // 把当前项目内的 AgentCard 转换成 Nacos A2A 注册中心要求的卡片模型。
    private com.alibaba.nacos.api.ai.model.a2a.AgentCard buildAgentCard(RegistrationPayload payload) {
        com.nacosa2a.core.model.AgentCard source = payload.agentCard();
        com.alibaba.nacos.api.ai.model.a2a.AgentCard target = new com.alibaba.nacos.api.ai.model.a2a.AgentCard();
        target.setName(payload.serviceName());
        target.setDescription(source.description());
        target.setVersion(source.version());
        target.setProtocolVersion("0.3.0");
        target.setUrl(payload.endpoint());
        target.setPreferredTransport(preferredTransport);
        target.setCapabilities(buildCapabilities());
        target.setSkills(buildSkills(source.methods()));
        target.setDefaultInputModes(List.of("application/json"));
        target.setDefaultOutputModes(List.of("application/json"));
        target.setSupportsAuthenticatedExtendedCard(Boolean.FALSE);
        return target;
    }

    // 当前 starter 默认只声明基础 A2A 能力，不额外暴露扩展能力。
    private AgentCapabilities buildCapabilities() {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(Boolean.FALSE);
        capabilities.setPushNotifications(Boolean.FALSE);
        capabilities.setStateTransitionHistory(Boolean.FALSE);
        capabilities.setExtensions(List.of());
        return capabilities;
    }

    // 每个注解方法映射为一条 skill，便于 AgentScope 读取功能描述。
    private List<AgentSkill> buildSkills(AgentMethodDefinition[] methods) {
        return Arrays.stream(methods)
                .map(this::buildSkill)
                .toList();
    }

    private AgentSkill buildSkill(AgentMethodDefinition method) {
        AgentSkill skill = new AgentSkill();
        skill.setId(method.name());
        skill.setName(method.name());
        skill.setDescription(method.description());
        skill.setTags(Arrays.asList(method.tags()));
        skill.setExamples(List.of());
        skill.setInputModes(List.of("application/json"));
        skill.setOutputModes(List.of("application/json"));
        return skill;
    }

    // 把 HTTP 地址拆成 Nacos AgentEndpoint 所需的 address/port/path 结构。
    private AgentEndpoint buildAgentEndpoint(RegistrationPayload payload) {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress(payload.host());
        endpoint.setPort(payload.port());
        endpoint.setTransport(preferredTransport);
        endpoint.setPath(payload.agentCard().endpoint());
        endpoint.setSupportTls(supportTls);
        endpoint.setVersion(payload.agentCard().version());
        return endpoint;
    }

    private String normalizePreferredTransport(String preferredTransport) {
        if (preferredTransport == null || preferredTransport.isBlank()) {
            return AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT;
        }
        return preferredTransport.trim().toUpperCase();
    }

    private static AiService createAiService(String serverAddr, String namespace, String username, String password) {
        Properties properties = new Properties();
        properties.setProperty(NACOS_SERVER_ADDR, serverAddr);
        if (namespace != null && !namespace.isBlank()) {
            properties.setProperty(NACOS_NAMESPACE, namespace);
        }
        if (username != null && !username.isBlank()) {
            properties.setProperty(NACOS_USERNAME, username);
        }
        if (password != null && !password.isBlank()) {
            properties.setProperty(NACOS_PASSWORD, password);
        }
        try {
            return AiFactory.createAiService(properties);
        } catch (NacosException exception) {
            throw new IllegalStateException("failed to create nacos a2a service", exception);
        }
    }
}
