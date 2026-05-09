package com.nacosa2a.demo;

import com.nacosa2a.core.model.RegistrationPayload;
import com.nacosa2a.core.registry.AgentRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.application.name=demo-agent-app",
        "server.port=18081",
        "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
        "spring.cloud.nacos.discovery.namespace=public",
        "spring.cloud.nacos.discovery.ip=127.0.0.1"
})
@AutoConfigureMockMvc
class A2aAgentDemoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingAgentRegistrar agentRegistrar;

    @Test
    void shouldRegisterDemoAgentAndInvokeGeneratedA2AEndpoint() throws Exception {
        assertEquals(2, agentRegistrar.registeredPayloads().size());
        RegistrationPayload payload = agentRegistrar.registeredPayloads().stream()
                .filter(item -> "order-agent".equals(item.serviceName()))
                .findFirst()
                .orElseThrow();
        assertEquals("http://127.0.0.1:18081/a2a/order-agent", payload.endpoint());
        assertEquals("order-agent", payload.agentCard().name());

        mockMvc.perform(post("/a2a/order-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drinkName\":\"Cheese Tea\",\"size\":\"Large\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent", is("order-agent")))
                .andExpect(jsonPath("$.drinkName", is("Cheese Tea")))
                .andExpect(jsonPath("$.size", is("Large")));
    }

    @TestConfiguration
    static class RegistrarTestConfiguration {

        @Bean
        @Primary
        RecordingAgentRegistrar recordingAgentRegistrar() {
            return new RecordingAgentRegistrar();
        }
    }

    static class RecordingAgentRegistrar implements AgentRegistrar {

        private final List<RegistrationPayload> registeredPayloads = new ArrayList<>();

        @Override
        public void register(RegistrationPayload payload) {
            registeredPayloads.add(payload);
        }

        @Override
        public void deregister(RegistrationPayload payload) {
        }

        List<RegistrationPayload> registeredPayloads() {
            return registeredPayloads;
        }
    }
}
