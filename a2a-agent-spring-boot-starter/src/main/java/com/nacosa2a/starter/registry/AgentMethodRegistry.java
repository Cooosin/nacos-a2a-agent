package com.nacosa2a.starter.registry;

import com.nacosa2a.core.card.AgentCardBuilder;
import com.nacosa2a.core.model.AgentCard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 维护运行时扫描到的 Agent 方法，并校验全局唯一的名称与访问地址。
 */
public class AgentMethodRegistry {

    private final AgentCardBuilder agentCardBuilder;
    private final List<AgentCard> agentCards = new ArrayList<>();
    private final Map<String, AgentCard> agentCardsByName = new HashMap<>();
    private final Set<String> registeredNames = new HashSet<>();
    private final Set<String> registeredEndpoints = new HashSet<>();

    public AgentMethodRegistry(AgentCardBuilder agentCardBuilder) {
        this.agentCardBuilder = agentCardBuilder;
    }

    /**
     * 注册单个 Agent 方法，并校验 name 与 endpoint 在全局范围内不能重复。
     */
    public void register(Method method) {
        AgentCard agentCard = agentCardBuilder.build(method);
        register(agentCard);
    }

    // AgentCard 列表、按名索引、名称集合和 endpoint 集合必须保持同一批已提交注册的数据。
    void register(AgentCard agentCard) {
        validateUniqueName(agentCard.name());
        try {
            validateUniqueEndpoint(agentCard.endpoint());
            agentCards.add(agentCard);
            agentCardsByName.put(agentCard.name(), agentCard);
        } catch (RuntimeException exception) {
            rollbackName(agentCard.name());
            throw exception;
        }
    }

    /**
     * 返回当前已注册的 AgentCard 列表。
     */
    public List<AgentCard> getAgentCards() {
        return List.copyOf(agentCards);
    }

    /**
     * 按 agent name 查询完整 AgentCard，用于发现后的 card 拉取接口。
     */
    public Optional<AgentCard> findByName(String agentName) {
        return Optional.ofNullable(agentCardsByName.get(agentName));
    }

    // 校验 Agent 名称在整个注册表内唯一。
    private void validateUniqueName(String name) {
        if (!registeredNames.add(name)) {
            throw new IllegalStateException("duplicate agent name: " + name);
        }
    }

    // 校验 Agent endpoint 在整个注册表内唯一。
    private void validateUniqueEndpoint(String endpoint) {
        if (!registeredEndpoints.add(endpoint)) {
            throw new IllegalStateException("duplicate agent endpoint: " + endpoint);
        }
    }

    // endpoint 校验失败时回滚本次预占用的名称，避免注册状态残留。
    private void rollbackName(String name) {
        registeredNames.remove(name);
    }
}
