package com.nacosa2a.core.registry;

import com.nacosa2a.core.model.RegistrationPayload;

/**
 * 定义 Agent 注册中心的注册与注销职责，供 starter 在应用生命周期内调用。
 */
public interface AgentRegistrar extends AutoCloseable {

    /**
     * 按注册载荷写入单个 Agent 的注册信息。
     */
    void register(RegistrationPayload payload);

    /**
     * 按注册载荷移除单个 Agent 的注册信息。
     */
    void deregister(RegistrationPayload payload);

    /**
     * 关闭注册器持有的底层资源，默认场景下无需额外处理。
     */
    @Override
    default void close() {
    }
}
