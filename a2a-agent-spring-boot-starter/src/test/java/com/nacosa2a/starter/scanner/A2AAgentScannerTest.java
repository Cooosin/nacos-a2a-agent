package com.nacosa2a.starter.scanner;

import com.nacosa2a.core.annotation.A2AAgent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class A2AAgentScannerTest {

    @Test
    void shouldRegisterOnlyHandlerMethodsAnnotatedWithA2AAgent() throws NoSuchMethodException {
        RecordingRegistrar registrar = new RecordingRegistrar();
        A2AAgentScanner scanner = new A2AAgentScanner(registrar::register);
        SampleController controller = new SampleController();
        Method agentMethod = SampleController.class.getDeclaredMethod("createOrder");
        Method plainMethod = SampleController.class.getDeclaredMethod("healthCheck");

        scanner.scan(List.of(
                new HandlerMethod(controller, agentMethod),
                new HandlerMethod(controller, plainMethod)
        ));

        assertEquals(1, registrar.registeredMethods().size());
        assertEquals(agentMethod, registrar.registeredMethods().get(0));
    }

    private static class RecordingRegistrar {

        private final List<Method> registeredMethods = new ArrayList<>();

        void register(Method method) {
            registeredMethods.add(method);
        }

        List<Method> registeredMethods() {
            return registeredMethods;
        }
    }

    private static class SampleController {

        @GetMapping("/orders")
        @A2AAgent(name = "order-agent", description = "创建订单")
        public String createOrder() {
            return "ok";
        }

        @GetMapping("/health")
        public String healthCheck() {
            return "ok";
        }
    }
}
