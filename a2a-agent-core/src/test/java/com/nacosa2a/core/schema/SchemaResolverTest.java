package com.nacosa2a.core.schema;

import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.core.card.AgentCardBuilder;
import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.core.model.AgentMethodDefinition;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaResolverTest {

    @Test
    void shouldMergeMethodParametersIntoUnifiedInputSchemaAndPreserveSourceMetadata() throws NoSuchMethodException {
        SchemaResolver resolver = new SchemaResolver(new OpenApiSchemaBuilder());
        Method method = SampleAgent.class.getDeclaredMethod("createOrder", String.class, Integer.class, OrderRequest.class);

        Schema<?> inputSchema = resolver.resolveInputSchema(method);

        assertEquals("object", inputSchema.getType());
        assertNotNull(inputSchema.getProperties());
        assertEquals(4, inputSchema.getProperties().size());
        assertTrue(inputSchema.getProperties().containsKey("orderId"));
        assertTrue(inputSchema.getProperties().containsKey("quantity"));
        assertTrue(inputSchema.getProperties().containsKey("skuCode"));
        assertTrue(inputSchema.getProperties().containsKey("remark"));

        Schema<?> orderIdSchema = schemaOf(inputSchema.getProperties(), "orderId");
        assertEquals("string", orderIdSchema.getType());
        assertEquals("path", orderIdSchema.getExtensions().get(SchemaResolver.PARAMETER_SOURCE_EXTENSION));

        Schema<?> quantitySchema = schemaOf(inputSchema.getProperties(), "quantity");
        assertEquals("integer", quantitySchema.getType());
        assertEquals("query", quantitySchema.getExtensions().get(SchemaResolver.PARAMETER_SOURCE_EXTENSION));

        Schema<?> skuCodeSchema = schemaOf(inputSchema.getProperties(), "skuCode");
        assertEquals("string", skuCodeSchema.getType());
        assertEquals("body", skuCodeSchema.getExtensions().get(SchemaResolver.PARAMETER_SOURCE_EXTENSION));

        Schema<?> remarkSchema = schemaOf(inputSchema.getProperties(), "remark");
        assertEquals("string", remarkSchema.getType());
        assertEquals("body", remarkSchema.getExtensions().get(SchemaResolver.PARAMETER_SOURCE_EXTENSION));
    }

    @Test
    void shouldResolveOutputSchemaFromMethodReturnTypeAndBuildAgentCard() throws NoSuchMethodException {
        SchemaResolver resolver = new SchemaResolver(new OpenApiSchemaBuilder());
        AgentCardBuilder agentCardBuilder = new AgentCardBuilder(resolver);
        Method method = SampleAgent.class.getDeclaredMethod("createOrder", String.class, Integer.class, OrderRequest.class);

        Schema<?> outputSchema = resolver.resolveOutputSchema(method);
        AgentCard agentCard = agentCardBuilder.build(method);

        assertEquals("object", outputSchema.getType());
        assertNotNull(outputSchema.getProperties());
        assertTrue(outputSchema.getProperties().containsKey("resultCode"));
        assertTrue(outputSchema.getProperties().containsKey("message"));

        assertEquals("order-agent", agentCard.name());
        assertEquals("创建订单 Agent", agentCard.description());
        assertEquals("1.2.0", agentCard.version());
        assertEquals("/a2a/order-agent", agentCard.endpoint());
        assertEquals("wechat", agentCard.metadata().get("channel"));
        assertEquals(1, agentCard.methods().length);

        AgentMethodDefinition methodDefinition = agentCard.methods()[0];
        assertEquals("createOrder", methodDefinition.name());
        assertEquals("创建订单 Agent", methodDefinition.description());
        assertEquals("/a2a/order-agent", methodDefinition.endpoint());
        assertInstanceOf(Schema.class, methodDefinition.inputSchema());
        assertInstanceOf(Schema.class, methodDefinition.outputSchema());
    }

    @Test
    void shouldPreserveGenericTypeInformationForInputAndOutputSchemas() throws NoSuchMethodException {
        SchemaResolver resolver = new SchemaResolver(new OpenApiSchemaBuilder());
        Method method = GenericSampleAgent.class.getDeclaredMethod("searchOrders", SearchRequest.class);

        Schema<?> inputSchema = resolver.resolveInputSchema(method);
        Schema<?> outputSchema = resolver.resolveOutputSchema(method);

        assertEquals("object", inputSchema.getType());
        Schema<?> idsSchema = schemaOf(inputSchema.getProperties(), "ids");
        assertEquals("array", idsSchema.getType());
        assertNotNull(idsSchema.getItems());
        assertEquals("string", idsSchema.getItems().getType());
        Schema<?> filterSchema = schemaOf(inputSchema.getProperties(), "filter");
        assertEquals("object", filterSchema.getType());
        assertInstanceOf(Schema.class, filterSchema.getAdditionalProperties());
        Schema<?> additionalProperties = (Schema<?>) filterSchema.getAdditionalProperties();
        assertEquals("integer", additionalProperties.getType());

        assertEquals("object", outputSchema.getType());
        Schema<?> itemsSchema = schemaOf(outputSchema.getProperties(), "items");
        assertEquals("array", itemsSchema.getType());
        assertNotNull(itemsSchema.getItems());
        assertEquals("object", itemsSchema.getItems().getType());
        assertNotNull(itemsSchema.getItems().getProperties());
        assertTrue(itemsSchema.getItems().getProperties().containsKey("orderNo"));
        assertTrue(itemsSchema.getItems().getProperties().containsKey("amount"));
        Schema<?> metadataSchema = schemaOf(outputSchema.getProperties(), "metadata");
        assertEquals("object", metadataSchema.getType());
        assertInstanceOf(Schema.class, metadataSchema.getAdditionalProperties());
        Schema<?> metadataValueSchema = (Schema<?>) metadataSchema.getAdditionalProperties();
        assertEquals("string", metadataValueSchema.getType());
    }

    @SuppressWarnings("unchecked")
    private static Schema<?> schemaOf(Map<String, Schema> properties, String name) {
        return properties.get(name);
    }

    private static class SampleAgent {

        @A2AAgent(
                name = "order-agent",
                description = "创建订单 Agent",
                version = "1.2.0",
                tags = {"order", "create"},
                metadata = {"channel=wechat"}
        )
        public OrderResponse createOrder(
                @PathVariable("orderId") String orderId,
                @RequestParam("quantity") Integer quantity,
                @RequestBody OrderRequest request) {
            return null;
        }
    }

    private static class GenericSampleAgent {

        @A2AAgent(
                name = "generic-agent",
                description = "泛型查询 Agent"
        )
        public PageResponse<OrderSummary> searchOrders(@RequestBody SearchRequest request) {
            return null;
        }
    }

    private record OrderRequest(String skuCode, String remark) {
    }

    private record OrderResponse(String resultCode, String message) {
    }

    private record SearchRequest(List<String> ids, Map<String, Integer> filter) {
    }

    private record PageResponse<T>(List<T> items, Map<String, String> metadata) {
    }

    private record OrderSummary(String orderNo, Integer amount) {
    }
}
