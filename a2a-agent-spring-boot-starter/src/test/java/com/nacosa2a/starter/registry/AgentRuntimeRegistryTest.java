package com.nacosa2a.starter.registry;

import com.nacosa2a.core.annotation.A2AAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectDuplicateAgentNameRegistration() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method firstMethod = SampleAgent.class.getDeclaredMethod("createOrder", CreateOrderRequest.class);
        Method duplicateNameMethod = SampleAgent.class.getDeclaredMethod("createOrderAlias", CreateOrderRequest.class);

        registry.register(firstMethod, bean);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> registry.register(duplicateNameMethod, bean));
        assertEquals("duplicate agent name: order-agent", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateEndpointRegistration() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method firstMethod = SampleAgent.class.getDeclaredMethod("createOrder", CreateOrderRequest.class);
        Method duplicateEndpointMethod = SampleAgent.class.getDeclaredMethod("createOrderEndpointAlias", CreateOrderRequest.class);

        registry.register(firstMethod, bean);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> registry.register(duplicateEndpointMethod, bean));
        assertEquals("duplicate agent endpoint: /a2a/order-agent", exception.getMessage());
    }

    @Test
    void shouldKeepOriginalHandlerAfterDuplicateRegistrationFails() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method firstMethod = SampleAgent.class.getDeclaredMethod("createOrder", CreateOrderRequest.class);
        Method duplicateNameMethod = SampleAgent.class.getDeclaredMethod("createOrderAlias", CreateOrderRequest.class);

        registry.register(firstMethod, bean);

        assertThrows(IllegalStateException.class, () -> registry.register(duplicateNameMethod, bean));

        Object result = registry.invokeByAgentName("order-agent", Map.of("orderId", "SO-1001"));
        assertTrue(result instanceof String);
        assertEquals("created:SO-1001", result);
    }

    @Test
    void shouldConvertRequestBodyForSingleRequestBodyParameter() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method createOrderMethod = SampleAgent.class.getDeclaredMethod("createOrder", CreateOrderRequest.class);

        registry.register(createOrderMethod, bean);

        Object result = registry.invokeByAgentName("order-agent", Map.of("orderId", "SO-1001"));
        assertEquals("created:SO-1001", result);
    }

    @Test
    void shouldBindUnifiedJsonBodyIntoAnnotatedMethodArguments() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method createDrinkMethod = SampleAgent.class.getDeclaredMethod(
                "createDrink",
                String.class,
                Integer.class,
                DrinkPayload.class,
                List.class
        );

        registry.register(createDrinkMethod, bean);

        Object result = registry.invokeByAgentName("drink-agent", Map.of(
                "shopId", "S001",
                "temperature", 2,
                "skuId", "LATTE",
                "cups", 3,
                "tags", List.of("milk", "hot")
        ));
        assertEquals("S001|2|LATTE|3|milk,hot", result);
    }

    @Test
    void shouldFilterTopLevelFieldsWhenBindingJavaBeanRequestBody() throws NoSuchMethodException {
        AgentRuntimeRegistry registry = new AgentRuntimeRegistry(objectMapper);
        SampleAgent bean = new SampleAgent();
        Method createTeaMethod = SampleAgent.class.getDeclaredMethod(
                "createTea",
                String.class,
                Integer.class,
                TeaPayload.class
        );

        registry.register(createTeaMethod, bean);

        Object result = registry.invokeByAgentName("tea-agent", Map.of(
                "shopId", "S002",
                "temperature", 1,
                "drinkCode", "BOBO",
                "cups", 2,
                "unexpectedField", "skip-me"
        ));
        assertEquals("S002|1|BOBO|2", result);
    }

    private static class SampleAgent {

        @A2AAgent(name = "order-agent", description = "创建订单")
        public String createOrder(@RequestBody CreateOrderRequest request) {
            return "created:" + request.orderId();
        }

        @A2AAgent(name = "order-agent", endpoint = "/a2a/order-agent-alias", description = "创建订单别名")
        public String createOrderAlias(@RequestBody CreateOrderRequest request) {
            return "alias:" + request.orderId();
        }

        @A2AAgent(name = "order-agent-alias", endpoint = "/a2a/order-agent", description = "创建订单地址别名")
        public String createOrderEndpointAlias(@RequestBody CreateOrderRequest request) {
            return "endpoint-alias:" + request.orderId();
        }

        @A2AAgent(name = "drink-agent", description = "创建饮品")
        public String createDrink(@PathVariable("shopId") String shopId,
                                  @RequestParam("temperature") Integer temperature,
                                  @RequestBody DrinkPayload payload,
                                  @RequestParam("tags") List<String> tags) {
            return shopId + "|" + temperature + "|" + payload.skuId() + "|" + payload.cups() + "|" + String.join(",", tags);
        }

        @A2AAgent(name = "tea-agent", description = "创建茶饮")
        public String createTea(@PathVariable("shopId") String shopId,
                                @RequestParam("temperature") Integer temperature,
                                @RequestBody TeaPayload payload) {
            return shopId + "|" + temperature + "|" + payload.getDrinkCode() + "|" + payload.getCups();
        }
    }

    private record CreateOrderRequest(String orderId) {
    }

    private record DrinkPayload(String skuId, Integer cups) {
    }

    private static class TeaPayload {

        private String drinkCode;
        private Integer cups;

        public String getDrinkCode() {
            return drinkCode;
        }

        public void setDrinkCode(String drinkCode) {
            this.drinkCode = drinkCode;
        }

        public Integer getCups() {
            return cups;
        }

        public void setCups(Integer cups) {
            this.cups = cups;
        }
    }
}
