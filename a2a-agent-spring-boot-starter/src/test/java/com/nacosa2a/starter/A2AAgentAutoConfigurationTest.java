package com.nacosa2a.starter;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.nacosa2a.core.annotation.A2AAgent;
import com.nacosa2a.core.model.RegistrationPayload;
import com.nacosa2a.core.registry.AgentRegistrar;
import com.nacosa2a.starter.config.A2ARegistrationProperties;
import com.nacosa2a.starter.registry.AgentMethodRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = A2AAgentAutoConfigurationTest.TestApplication.class,
        properties = {
                "spring.application.name=a2a-agent-test",
                "server.port=19090",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
                "spring.cloud.nacos.discovery.namespace=public",
                "spring.cloud.nacos.discovery.group=DEFAULT_GROUP"
        }
)
@AutoConfigureMockMvc
class A2AAgentAutoConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentMethodRegistry agentMethodRegistry;

    @Autowired
    private RecordingAgentRegistrar agentRegistrar;

    @Autowired
    private NacosDiscoveryProperties nacosDiscoveryProperties;

    @Test
    void shouldRegisterAnnotatedAgentOnStartupAndKeepGeneratedEndpointCallable() throws Exception {
        assertEquals(1, agentMethodRegistry.getAgentCards().size());
        assertEquals("order-agent", agentMethodRegistry.getAgentCards().get(0).name());
        assertEquals("/a2a/order-agent", agentMethodRegistry.getAgentCards().get(0).endpoint());

        assertEquals(1, agentRegistrar.registeredPayloads().size());
        assertEquals(nacosDiscoveryProperties.getIp(), agentRegistrar.registeredPayloads().get(0).host());
        RegistrationPayload payload = agentRegistrar.registeredPayloads().get(0);
        assertEquals("order-agent", payload.serviceName());
        assertEquals("DEFAULT_GROUP", payload.groupName());
        assertEquals("public", payload.namespace());
        assertEquals("http://" + nacosDiscoveryProperties.getIp() + ":19090/a2a/order-agent", payload.endpoint());
        assertEquals("order-agent", payload.agentCard().name());
        assertEquals("创建订单", payload.agentCard().description());

        mockMvc.perform(post("/a2a/order-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drinkName\":\"Cheese Tea\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent", is("order-agent")))
                .andExpect(jsonPath("$.drinkName", is("Cheese Tea")));

        mockMvc.perform(get("/a2a/order-agent/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("order-agent")))
                .andExpect(jsonPath("$.description", is("创建订单")))
                .andExpect(jsonPath("$.endpoint", is("/a2a/order-agent")));
    }

    @Test
    void shouldReturnNotFoundWhenAgentCardDoesNotExist() throws Exception {
        mockMvc.perform(get("/a2a/missing-agent/card"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldStartApplicationWhenDefaultRegistrationIsDisabledWithoutHostAndPort() {
        assertDoesNotThrow(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(DisabledRegistrationApplication.class)
                    .properties(
                            "spring.application.name=a2a-agent-disabled-test",
                            "server.port=0",
                            "a2a.registration.enabled=false",
                            "spring.cloud.service-registry.auto-registration.enabled=false"
                    )
                    .run()) {
            }
        });
    }

    @Test
    void shouldStartApplicationWhenDefaultRegistrationIsMisconfigured() {
        assertDoesNotThrow(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(MisconfiguredRegistrationApplication.class)
                    .properties(
                            "spring.application.name=a2a-agent-misconfigured-test",
                            "server.port=0",
                            "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
                            "spring.cloud.service-registry.auto-registration.enabled=false"
                    )
                    .run()) {
            }
        });
    }

    @Test
    void shouldPreferExplicitA2ARegistrationSettingsOverDiscoveryDefaults() {
        A2ARegistrationProperties properties = new A2ARegistrationProperties();
        properties.setServerAddr("override-nacos:8848");
        properties.setNamespace("override-namespace");
        properties.setUsername("override-user");
        properties.setPassword("override-pass");
        properties.setGroupName("override-group");
        properties.setHost("10.0.0.1");
        properties.setPort(28080);

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.cloud.nacos.discovery.server-addr", "discovery-nacos:8848",
                "spring.cloud.nacos.discovery.namespace", "discovery-namespace",
                "spring.cloud.nacos.discovery.username", "discovery-user",
                "spring.cloud.nacos.discovery.password", "discovery-pass",
                "server.port", "19090"
        )));

        AgentRegistrar registrar = new com.nacosa2a.starter.config.A2AAgentAutoConfiguration().agentRegistrar(properties, environment,
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(NacosDiscoveryProperties.class));

        assertDoesNotThrow(() -> registrar.close());
    }

    @SpringBootApplication
    @Import({SampleAgentController.class, TestRegistrarConfiguration.class})
    static class TestApplication {
    }

    @SpringBootApplication
    @Import(SampleAgentController.class)
    static class DisabledRegistrationApplication {
    }

    @SpringBootApplication
    @Import(SampleAgentController.class)
    static class MisconfiguredRegistrationApplication {
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestRegistrarConfiguration {

        @Bean
        @Primary
        RecordingAgentRegistrar recordingAgentRegistrar() {
            return new RecordingAgentRegistrar();
        }
    }

    static class RecordingAgentRegistrar implements AgentRegistrar {

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

    @RestController
    static class SampleAgentController {

        @PostMapping("/orders")
        @A2AAgent(name = "order-agent", description = "创建订单")
        public Map<String, Object> createOrder(@RequestBody OrderRequest request) {
            return Map.of(
                    "agent", "order-agent",
                    "drinkName", request.drinkName()
            );
        }
    }

    record OrderRequest(String drinkName) {
    }
}
