package com.nacosa2a.core.registry;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.exception.NacosException;
import com.nacosa2a.core.model.AgentMethodDefinition;
import com.nacosa2a.core.model.RegistrationPayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosAgentRegistrarTest {

    @Test
    void shouldReleaseAgentCardAndRegisterEndpoint() throws NacosException {
        RecordingAiService a2aService = new RecordingAiService();
        NacosAgentRegistrar registrar = new NacosAgentRegistrar(a2aService.createProxy(), true, "JSONRPC", true, true);
        RegistrationPayload payload = createPayload();

        registrar.register(payload);

        assertNotNull(a2aService.releasedAgentCard);
        assertEquals("order-agent", a2aService.releasedAgentCard.getName());
        assertEquals("创建订单 Agent", a2aService.releasedAgentCard.getDescription());
        assertEquals("1.0.0", a2aService.releasedAgentCard.getVersion());
        assertEquals("http://127.0.0.1:18080/a2a/order-agent", a2aService.releasedAgentCard.getUrl());
        assertEquals("JSONRPC", a2aService.releasedAgentCard.getPreferredTransport());
        assertEquals("SERVICE", a2aService.releaseRegistrationType);
        assertTrue(a2aService.releaseAsLatest);
        assertNotNull(a2aService.releasedAgentCard.getCapabilities());
        assertEquals(Boolean.FALSE, a2aService.releasedAgentCard.getCapabilities().getStreaming());
        assertEquals(1, a2aService.releasedAgentCard.getSkills().size());
        AgentSkill skill = a2aService.releasedAgentCard.getSkills().get(0);
        assertEquals("createOrder", skill.getId());
        assertEquals("createOrder", skill.getName());
        assertEquals("创建订单", skill.getDescription());
        assertEquals(List.of("order"), skill.getTags());

        assertEquals("order-agent", a2aService.registeredAgentName);
        assertNotNull(a2aService.registeredEndpoint);
        assertEquals("127.0.0.1", a2aService.registeredEndpoint.getAddress());
        assertEquals(18080, a2aService.registeredEndpoint.getPort());
        assertEquals("JSONRPC", a2aService.registeredEndpoint.getTransport());
        assertEquals("/a2a/order-agent", a2aService.registeredEndpoint.getPath());
        assertTrue(a2aService.registeredEndpoint.isSupportTls());
        assertEquals("1.0.0", a2aService.registeredEndpoint.getVersion());
    }

    @Test
    void shouldDeregisterPreviouslyRegisteredEndpoint() throws NacosException {
        RecordingAiService a2aService = new RecordingAiService();
        NacosAgentRegistrar registrar = new NacosAgentRegistrar(a2aService.createProxy(), true, "JSONRPC", false, true);
        RegistrationPayload payload = createPayload();

        registrar.register(payload);
        registrar.deregister(payload);
        registrar.close();

        assertEquals("order-agent", a2aService.deregisteredAgentName);
        assertNotNull(a2aService.deregisteredEndpoint);
        assertEquals("127.0.0.1", a2aService.deregisteredEndpoint.getAddress());
        assertEquals(18080, a2aService.deregisteredEndpoint.getPort());
        assertFalse(a2aService.deregisteredEndpoint.isSupportTls());
        assertTrue(a2aService.shutDownCalled);
    }

    @Test
    void shouldSkipEndpointRegistrationWhenDisabled() throws NacosException {
        RecordingAiService a2aService = new RecordingAiService();
        NacosAgentRegistrar registrar = new NacosAgentRegistrar(a2aService.createProxy(), false, "JSONRPC", false, true);

        registrar.register(createPayload());

        assertNotNull(a2aService.releasedAgentCard);
        assertEquals(null, a2aService.registeredEndpoint);
    }

    private RegistrationPayload createPayload() {
        Map<String, String> metadata = Map.of("team", "barista");
        AgentMethodDefinition methodDefinition = new AgentMethodDefinition(
                "createOrder",
                "创建订单",
                "/orders",
                new String[]{"order"},
                metadata,
                null,
                null
        );
        com.nacosa2a.core.model.AgentCard agentCard = new com.nacosa2a.core.model.AgentCard(
                "order-agent",
                "创建订单 Agent",
                "1.0.0",
                "/a2a/order-agent",
                new String[]{"order"},
                metadata,
                new AgentMethodDefinition[]{methodDefinition}
        );
        return new RegistrationPayload(
                "order-agent",
                "DEFAULT_GROUP",
                "public",
                "http://127.0.0.1:18080/a2a/order-agent",
                metadata,
                "127.0.0.1",
                18080,
                agentCard
        );
    }

    private static class RecordingAiService implements InvocationHandler {

        private AgentCard releasedAgentCard;
        private String releaseRegistrationType;
        private boolean releaseAsLatest;
        private String registeredAgentName;
        private AgentEndpoint registeredEndpoint;
        private String deregisteredAgentName;
        private AgentEndpoint deregisteredEndpoint;
        private boolean shutDownCalled;

        private AiService createProxy() {
            return (AiService) Proxy.newProxyInstance(
                    AiService.class.getClassLoader(),
                    new Class<?>[]{AiService.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("releaseAgentCard".equals(methodName) && args != null && args.length == 3) {
                this.releasedAgentCard = (AgentCard) args[0];
                this.releaseRegistrationType = (String) args[1];
                this.releaseAsLatest = (Boolean) args[2];
                return null;
            }
            if ("registerAgentEndpoint".equals(methodName) && args != null && args.length == 2) {
                this.registeredAgentName = (String) args[0];
                this.registeredEndpoint = (AgentEndpoint) args[1];
                return null;
            }
            if ("deregisterAgentEndpoint".equals(methodName) && args != null && args.length == 2) {
                this.deregisteredAgentName = (String) args[0];
                this.deregisteredEndpoint = (AgentEndpoint) args[1];
                return null;
            }
            if ("shutdown".equals(methodName)) {
                this.shutDownCalled = true;
                return null;
            }
            if ("getAgentCard".equals(methodName)) {
                return null;
            }
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(methodName)) {
                    return getClass().getName();
                }
                if ("hashCode".equals(methodName)) {
                    return System.identityHashCode(this);
                }
                if ("equals".equals(methodName)) {
                    return proxy == args[0];
                }
            }
            throw new UnsupportedOperationException(methodName);
        }
    }
}
