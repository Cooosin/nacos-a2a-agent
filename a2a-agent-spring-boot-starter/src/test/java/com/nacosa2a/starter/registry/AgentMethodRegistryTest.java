package com.nacosa2a.starter.registry;

import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.core.card.AgentCardBuilder;
import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.core.schema.OpenApiSchemaBuilder;
import com.nacosa2a.core.schema.SchemaResolver;
import org.junit.jupiter.api.Test;

import com.nacosa2a.core.model.AgentMethodDefinition;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMethodRegistryTest {

    private final AgentCardBuilder agentCardBuilder = new AgentCardBuilder(new SchemaResolver(new OpenApiSchemaBuilder()));

    @Test
    void shouldRegisterAnnotatedMethodsWhenNameAndEndpointAreUnique() throws NoSuchMethodException {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);
        Method createOrder = SampleAgents.class.getDeclaredMethod("createOrder");
        Method createRefund = SampleAgents.class.getDeclaredMethod("createRefund");

        registry.register(createOrder);
        registry.register(createRefund);

        assertEquals(2, registry.getAgentCards().size());
        assertEquals("order-agent", registry.getAgentCards().get(0).name());
        assertEquals("refund-agent", registry.getAgentCards().get(1).name());
    }

    @Test
    void shouldRejectDuplicateNameGlobally() throws NoSuchMethodException {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);
        Method createOrder = SampleAgents.class.getDeclaredMethod("createOrder");
        Method createOrderV2 = SampleAgents.class.getDeclaredMethod("createOrderV2");

        registry.register(createOrder);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> registry.register(createOrderV2));
        assertEquals("duplicate agent name: order-agent", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateEndpointGlobally() throws NoSuchMethodException {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);
        Method createOrder = SampleAgents.class.getDeclaredMethod("createOrder");
        Method createRefundAlias = SampleAgents.class.getDeclaredMethod("createRefundAlias");

        registry.register(createOrder);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> registry.register(createRefundAlias));
        assertEquals("duplicate agent endpoint: /a2a/order-agent", exception.getMessage());
    }

    @Test
    void shouldFindAgentCardByNameAfterRegistration() throws NoSuchMethodException {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);
        Method createOrder = SampleAgents.class.getDeclaredMethod("createOrder");

        registry.register(createOrder);

        AgentCard agentCard = registry.findByName("order-agent").orElseThrow();
        assertEquals("order-agent", agentCard.name());
        assertEquals("/a2a/order-agent", agentCard.endpoint());
    }

    @Test
    void shouldReturnEmptyWhenAgentNameDoesNotExist() {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);

        assertTrue(registry.findByName("missing-agent").isEmpty());
    }

    @Test
    void shouldRollbackNameReservationWhenDuplicateEndpointIsRejected() {
        AgentMethodRegistry registry = new AgentMethodRegistry(agentCardBuilder);
        AgentCard existingAgent = new AgentCard("order-agent", "创建订单", null, "/a2a/order-agent", new String[0], Map.of(), new AgentMethodDefinition[0]);
        AgentCard rejectedAgent = new AgentCard("refund-agent-alias", "退款别名", null, "/a2a/order-agent", new String[0], Map.of(), new AgentMethodDefinition[0]);
        AgentCard retryAgent = new AgentCard("refund-agent-alias", "退款别名重试", null, "/a2a/refund-agent-alias", new String[0], Map.of(), new AgentMethodDefinition[0]);

        registry.register(existingAgent);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> registry.register(rejectedAgent));
        assertEquals("duplicate agent endpoint: /a2a/order-agent", exception.getMessage());
        assertTrue(registry.findByName("refund-agent-alias").isEmpty());

        registry.register(retryAgent);
        assertEquals(2, registry.getAgentCards().size());
        assertEquals("refund-agent-alias", registry.getAgentCards().get(1).name());
        assertEquals("/a2a/refund-agent-alias", registry.getAgentCards().get(1).endpoint());
        assertTrue(registry.findByName("refund-agent-alias").isPresent());
    }

    private static class SampleAgents {

        @A2AAgent(name = "order-agent", description = "创建订单")
        public String createOrder() {
            return "ok";
        }

        @A2AAgent(name = "refund-agent", description = "创建退款")
        public String createRefund() {
            return "ok";
        }

        @A2AAgent(name = "order-agent", description = "创建订单V2")
        public String createOrderV2() {
            return "ok";
        }

        @A2AAgent(name = "refund-agent-alias", endpoint = "/a2a/order-agent", description = "退款别名")
        public String createRefundAlias() {
            return "ok";
        }
    }
}
