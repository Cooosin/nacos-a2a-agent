package com.nacosa2a.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 声明 A2A Agent 注册到 Nacos 所需的基础配置。
 */
@ConfigurationProperties(prefix = "a2a.registration")
public class A2ARegistrationProperties {

    // 是否开启启动注册流程。
    private boolean enabled = true;

    // Nacos 服务地址，优先使用显式 A2A 配置，否则回退到现有 discovery 配置。
    private String serverAddr;

    // Nacos 命名空间，优先使用显式 A2A 配置，否则回退到现有 discovery 配置。
    private String namespace;

    // Nacos 用户名，优先使用显式 A2A 配置，否则回退到现有 discovery 配置。
    private String username;

    // Nacos 密码，优先使用显式 A2A 配置，否则回退到现有 discovery 配置。
    private String password;

    // Nacos 分组，优先使用显式 A2A 配置，否则回退到现有 discovery 配置。
    private String groupName;

    // 当前服务对外注册 Host，未配置时复用 NacosDiscoveryProperties 已解析的 IP。
    private String host;

    // 当前服务对外注册端口，未配置时回退到 server.port。
    private Integer port;

    // 注册到 Nacos A2A AgentCard 的首选传输协议。
    private String preferredTransport = "JSONRPC";

    // 是否将当前注册版本标记为 latest。
    private boolean registerAsLatest = true;

    // 是否同时注册 AgentEndpoint，供 AgentScope 按 agent name 解析可达地址。
    private boolean enabledRegisterEndpoint = true;

    // AgentEndpoint 是否声明支持 TLS。
    private boolean supportTls;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getPreferredTransport() {
        return preferredTransport;
    }

    public void setPreferredTransport(String preferredTransport) {
        this.preferredTransport = preferredTransport;
    }

    public boolean isRegisterAsLatest() {
        return registerAsLatest;
    }

    public void setRegisterAsLatest(boolean registerAsLatest) {
        this.registerAsLatest = registerAsLatest;
    }

    public boolean isEnabledRegisterEndpoint() {
        return enabledRegisterEndpoint;
    }

    public void setEnabledRegisterEndpoint(boolean enabledRegisterEndpoint) {
        this.enabledRegisterEndpoint = enabledRegisterEndpoint;
    }

    public boolean isSupportTls() {
        return supportTls;
    }

    public void setSupportTls(boolean supportTls) {
        this.supportTls = supportTls;
    }
}
