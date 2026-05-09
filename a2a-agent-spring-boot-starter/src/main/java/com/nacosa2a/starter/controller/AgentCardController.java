package com.nacosa2a.starter.controller;

import com.nacosa2a.core.model.AgentCard;
import com.nacosa2a.starter.registry.AgentMethodRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 暴露按 agent name 读取完整 AgentCard 的统一入口，供发现后拉取详细描述使用。
 */
@RestController
public class AgentCardController {

    private final AgentMethodRegistry agentMethodRegistry;

    public AgentCardController(AgentMethodRegistry agentMethodRegistry) {
        this.agentMethodRegistry = agentMethodRegistry;
    }

    @GetMapping("/a2a/{agentName}/card")
    public ResponseEntity<AgentCard> getAgentCard(@PathVariable("agentName") String agentName) {
        AgentCard agentCard = agentMethodRegistry.findByName(agentName)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "agent not found: " + agentName));
        return ResponseEntity.ok(agentCard);
    }
}
