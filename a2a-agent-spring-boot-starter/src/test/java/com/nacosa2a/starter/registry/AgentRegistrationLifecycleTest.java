package com.nacosa2a.starter.registry;

import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.core.model.AgentMethodDefinition;
import com.nacosa2a.core.model.RegistrationPayload;
import com.nacosa2a.core.registry.AgentRegistrar;
import com.nacosa2a.starter.config.A2ARegistrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentRegistrationLifecycleTest {

    @Test
    void shouldAttemptStartupRegistrationForEachAgentCard() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        agentMethodRegistry.register(createAgentCard("refund-agent"));
        RecordingAgentRegistrar agentRegistrar = new RecordingAgentRegistrar();
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                createRegistrationProperties(),
                createEnvironment("demo-agent-app"),
                null
        );

        lifecycle.start();

        assertEquals(2, agentRegistrar.registeredPayloads().size());
        assertEquals("order-agent", agentRegistrar.registeredPayloads().get(0).agentCard().name());
        assertEquals("refund-agent", agentRegistrar.registeredPayloads().get(1).agentCard().name());
        assertTrue(lifecycle.isRunning());
    }

    @Test
    void shouldLogAndContinueWhenRegistrationFails() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        agentMethodRegistry.register(createAgentCard("refund-agent"));
        FailingAgentRegistrar agentRegistrar = new FailingAgentRegistrar();
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                createRegistrationProperties(),
                createEnvironment("demo-agent-app"),
                null
        );

        lifecycle.start();

        assertEquals(2, agentRegistrar.attemptedPayloads().size());
        assertTrue(lifecycle.isRunning());
    }

    @Test
    void shouldSkipStartupRegistrationWhenDisabled() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        RecordingAgentRegistrar agentRegistrar = new RecordingAgentRegistrar();
        A2ARegistrationProperties properties = createRegistrationProperties();
        properties.setEnabled(false);
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                properties,
                createEnvironment(null),
                null
        );

        lifecycle.start();

        assertTrue(agentRegistrar.registeredPayloads().isEmpty());
        assertTrue(lifecycle.isRunning());
    }

    @Test
    void shouldLogAndContinueWhenApplicationNameIsMissing() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        RecordingAgentRegistrar agentRegistrar = new RecordingAgentRegistrar();
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                createRegistrationProperties(),
                createEnvironment(null),
                null
        );

        lifecycle.start();

        assertTrue(agentRegistrar.registeredPayloads().isEmpty());
        assertTrue(lifecycle.isRunning());
    }

    @Test
    void shouldDeregisterOnlySuccessfulRegistrationsOnShutdown() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        agentMethodRegistry.register(createAgentCard("refund-agent"));
        RecordingAgentRegistrar agentRegistrar = new RecordingAgentRegistrar();
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                createRegistrationProperties(),
                createEnvironment("demo-agent-app"),
                null
        );

        lifecycle.start();
        lifecycle.stop();

        assertEquals(2, agentRegistrar.deregisteredPayloads().size());
        assertEquals("order-agent", agentRegistrar.deregisteredPayloads().get(0).agentCard().name());
        assertEquals("refund-agent", agentRegistrar.deregisteredPayloads().get(1).agentCard().name());
    }

    @Test
    void shouldBuildDistinctServiceNameForEachAgentCard() {
        AgentMethodRegistry agentMethodRegistry = new AgentMethodRegistry(null);
        agentMethodRegistry.register(createAgentCard("order-agent"));
        agentMethodRegistry.register(createAgentCard("refund-agent"));
        RecordingAgentRegistrar agentRegistrar = new RecordingAgentRegistrar();
        AgentRegistrationLifecycle lifecycle = new AgentRegistrationLifecycle(
                agentMethodRegistry,
                singleBeanProvider(agentRegistrar),
                createRegistrationProperties(),
                createEnvironment("demo-agent-app"),
                null
        );

        lifecycle.start();

        assertEquals("order-agent", agentRegistrar.registeredPayloads().get(0).serviceName());
        assertEquals("refund-agent", agentRegistrar.registeredPayloads().get(1).serviceName());
    }

    private AgentCard createAgentCard(String name) {
        AgentMethodDefinition methodDefinition = new AgentMethodDefinition(
                "handle",
                "处理请求",
                "/" + name,
                new String[]{"demo"},
                Map.of("team", "barista"),
                null,
                null
        );
        return new AgentCard(
                name,
                "demo agent",
                "1.0.0",
                "/a2a/" + name,
                new String[]{"demo"},
                Map.of("team", "barista"),
                new AgentMethodDefinition[]{methodDefinition}
        );
    }

    private A2ARegistrationProperties createRegistrationProperties() {
        A2ARegistrationProperties properties = new A2ARegistrationProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(18080);
        properties.setNamespace("public");
        properties.setGroupName("DEFAULT_GROUP");
        return properties;
    }

    private StandardEnvironment createEnvironment(String applicationName) {
        StandardEnvironment environment = new StandardEnvironment();
        if (applicationName != null) {
            environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("spring.application.name", applicationName)));
        }
        return environment;
    }

    private ObjectProvider<AgentRegistrar> singleBeanProvider(AgentRegistrar agentRegistrar) {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("agentRegistrar", agentRegistrar);
        context.refresh();
        return context.getBeanProvider(AgentRegistrar.class);
    }

    private static class RecordingAgentRegistrar implements AgentRegistrar {

        private final List<RegistrationPayload> registeredPayloads = new ArrayList<>();
        private final List<RegistrationPayload> deregisteredPayloads = new ArrayList<>();

        @Override
        public void register(RegistrationPayload payload) {
            registeredPayloads.add(payload);
        }

        @Override
        public void deregister(RegistrationPayload payload) {
            deregisteredPayloads.add(payload);
        }

        List<RegistrationPayload> registeredPayloads() {
            return registeredPayloads;
        }

        List<RegistrationPayload> deregisteredPayloads() {
            return deregisteredPayloads;
        }
    }

    private static class FailingAgentRegistrar implements AgentRegistrar {

        private final List<RegistrationPayload> attemptedPayloads = new ArrayList<>();

        @Override
        public void register(RegistrationPayload payload) {
            attemptedPayloads.add(payload);
            throw new IllegalStateException("nacos unavailable");
        }

        @Override
        public void deregister(RegistrationPayload payload) {
        }

        List<RegistrationPayload> attemptedPayloads() {
            return attemptedPayloads;
        }
    }
}
