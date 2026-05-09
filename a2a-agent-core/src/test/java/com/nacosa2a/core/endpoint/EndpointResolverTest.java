package com.nacosa2a.core.endpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointResolverTest {

    @Test
    void shouldGenerateDefaultEndpointFromAgentName() {
        String endpoint = EndpointResolver.resolveAgentEndpoint("order-agent", "");

        assertEquals("/a2a/order-agent", endpoint);
    }

    @Test
    void shouldUseCustomEndpointWhenProvided() {
        String endpoint = EndpointResolver.resolveAgentEndpoint("order-agent", "/custom/order-agent/");

        assertEquals("/custom/order-agent", endpoint);
    }

    @Test
    void shouldAddLeadingSlashForCustomEndpoint() {
        String endpoint = EndpointResolver.resolveAgentEndpoint("order-agent", "custom/order-agent/");

        assertEquals("/custom/order-agent", endpoint);
    }

    @Test
    void shouldFallbackToDefaultEndpointWhenCustomEndpointIsBlank() {
        String endpoint = EndpointResolver.resolveAgentEndpoint("order-agent", "   ");

        assertEquals("/a2a/order-agent", endpoint);
    }

    @Test
    void shouldRejectBlankNameWhenCustomEndpointMissing() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> EndpointResolver.resolveAgentEndpoint("   ", null));

        assertEquals("name must not be blank", exception.getMessage());
    }
}
