package com.nacosa2a.starter.controller;

import com.nacosa2a.starter.registry.AgentRuntimeRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露统一的 A2A 协议入口，并按 agent name 转发到启动时注册的方法。
 */
@RestController
public class A2AProtocolController {

    private final AgentRuntimeRegistry agentRuntimeRegistry;

    public A2AProtocolController(AgentRuntimeRegistry agentRuntimeRegistry) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
    }

    /**
     * 接收生成的 /a2a/{agentName} 请求，并调用对应的 A2AAgent 方法。
     */
    @PostMapping("/a2a/{agentName}")
    public ResponseEntity<Object> invoke(@PathVariable("agentName") String agentName, @RequestBody(required = false) Object requestBody) {
        Object response = agentRuntimeRegistry.invokeByAgentName(agentName, requestBody);
        return ResponseEntity.ok(response);
    }
}
