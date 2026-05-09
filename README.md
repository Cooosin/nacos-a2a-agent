# Nacos A2A Agent Starter

一个基于 Spring Boot 的 A2A Agent 注册项目。

这个项目的目标是：

- 通过注解方式把业务接口声明为 A2A Agent
- 在应用启动时自动构建 AgentCard
- 将 AgentCard 和 AgentEndpoint 注册到 Nacos A2A 注册中心
- 让 AgentScope 可以通过 Nacos 正常发现和调用这些 Agent

---

## 1. 适用场景

如果你有一个 Spring Boot 项目，希望把项目里的某些 HTTP 接口暴露成 Agent，并注册到 Nacos 供 AgentScope 发现，可以使用这套 starter。

当前实现已经对齐 AgentScope Java 的 Nacos A2A 发现方式：

- 注册时走 Nacos A2A API
- 发布 `AgentCard`
- 发布 `AgentEndpoint`

不是旧的：

- `NamingService.registerInstance(...)`
- metadata 轻量注册
- 再通过 `/a2a/{agentName}/card` 拉取卡片

---

## 2. 依赖前提

### 2.1 需要依赖 Nacos 服务发现

这个项目需要建立在 **Spring Cloud Nacos 服务发现** 的基础上使用。

也就是说，你的业务项目本身需要已经接入：

- `spring-cloud-starter-alibaba-nacos-discovery`

本项目会复用这套 Nacos 服务发现配置作为 A2A 注册的基础连接配置来源。

---

## 3. 需要引入的依赖

### 3.1 在已有 Spring Boot + Nacos Discovery 项目基础上引入

如果你的项目已经有：

- `spring-boot-starter-web`
- `spring-cloud-starter-alibaba-nacos-discovery`

那么只需要额外引入本 starter：

```xml
<dependency>
    <groupId>com.nacosa2a</groupId>
    <artifactId>a2a-agent-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 3.2 一个完整示例依赖

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <dependency>
        <groupId>com.nacosa2a</groupId>
        <artifactId>a2a-agent-spring-boot-starter</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

---

## 4. 配置方式

### 4.1 基础配置

下面这部分是必须有的基础配置。

```properties
spring.application.name=demo-agent-app
server.port=8083

spring.cloud.nacos.discovery.server-addr=test-nacos.lan.heytea.com:8848
spring.cloud.nacos.discovery.namespace=aad97e70-1a44-4158-b07e-574f512cbd90
spring.cloud.nacos.discovery.group=DEFAULT_GROUP
spring.cloud.nacos.discovery.username=H004716
spring.cloud.nacos.discovery.password=H004716
spring.cloud.nacos.discovery.ip=127.0.0.1
```

说明：

- `spring.cloud.nacos.discovery.server-addr`
  - Nacos 地址
- `spring.cloud.nacos.discovery.namespace`
  - Nacos 命名空间
- `spring.cloud.nacos.discovery.group`
  - Nacos 分组
- `spring.cloud.nacos.discovery.username/password`
  - Nacos 认证信息
- `spring.cloud.nacos.discovery.ip`
  - 当前服务对外注册 IP

建议显式配置 `spring.cloud.nacos.discovery.ip`，因为 A2A 注册最终需要明确的可访问地址。

---

### 4.2 A2A 注册配置

```properties
a2a.registration.enabled=true
a2a.registration.preferred-transport=JSONRPC
a2a.registration.register-as-latest=true
a2a.registration.enabled-register-endpoint=true
a2a.registration.support-tls=false
```

可选覆盖项：

```properties
a2a.registration.server-addr=test-nacos.lan.heytea.com:8848
a2a.registration.namespace=aad97e70-1a44-4158-b07e-574f512cbd90
a2a.registration.group-name=DEFAULT_GROUP
a2a.registration.username=H004716
a2a.registration.password=H004716
a2a.registration.host=127.0.0.1
a2a.registration.port=8083
```

这些字段的含义：

- `a2a.registration.enabled`
  - 是否开启自动注册

- `a2a.registration.preferred-transport`
  - 注册到 `AgentCard.preferredTransport` 的协议类型
  - 默认 `JSONRPC`

- `a2a.registration.register-as-latest`
  - 是否把当前版本标记为 latest

- `a2a.registration.enabled-register-endpoint`
  - 是否同时注册 `AgentEndpoint`

- `a2a.registration.support-tls`
  - 是否声明当前 endpoint 使用 HTTPS

- `a2a.registration.server-addr`
  - 显式覆盖 Nacos 地址
  - 未配置时回退到 `spring.cloud.nacos.discovery.server-addr`

- `a2a.registration.namespace`
  - 显式覆盖 namespace
  - 未配置时回退到 `spring.cloud.nacos.discovery.namespace`

- `a2a.registration.group-name`
  - 显式覆盖 group
  - 未配置时回退到 `spring.cloud.nacos.discovery.group`

- `a2a.registration.username/password`
  - 显式覆盖 Nacos 认证信息
  - 未配置时回退到 `spring.cloud.nacos.discovery.username/password`

- `a2a.registration.host`
  - 显式覆盖对外注册 IP
  - 未配置时回退到 `spring.cloud.nacos.discovery.ip`

- `a2a.registration.port`
  - 显式覆盖对外注册端口
  - 未配置时回退到 `server.port`

---

## 5. 如何声明一个 Agent

在 Spring MVC 的方法上添加 `@A2AAgent` 即可。

```java
@RestController
public class OrderAgentController {

    @PostMapping("/orders")
    @A2AAgent(name = "order-agent", description = "创建订单")
    public Map<String, Object> createOrder(@RequestBody OrderRequest request) {
        return Map.of(
                "agent", "order-agent",
                "drinkName", request.drinkName(),
                "size", request.size()
        );
    }

    public record OrderRequest(String drinkName, String size) {
    }
}
```

支持的注解字段：

- `name`
  - Agent 名称
- `description`
  - Agent 描述
- `version`
  - Agent 版本，默认 `1.0.0`
- `endpoint`
  - 自定义 A2A 入口路径，未配置时默认 `/a2a/{name}`
- `tags`
  - Agent 标签
- `metadata`
  - 扩展元数据，格式 `key=value`

---

## 6. 启动后会发生什么

应用启动后 starter 会自动完成这些事情：

1. 扫描 Spring MVC 的 `HandlerMethod`
2. 找出所有带 `@A2AAgent` 的方法
3. 为每个方法构建一个 AgentCard
4. 注册统一 A2A 入口 `/a2a/{agentName}`
5. 注册 AgentCard 查询入口 `/a2a/{agentName}/card`
6. 调用 Nacos A2A API：
   - 发布 `AgentCard`
   - 发布 `AgentEndpoint`

---

## 7. 暴露出的访问入口

### 7.1 A2A 调用入口

```http
POST /a2a/{agentName}
```

例如：

```http
POST /a2a/order-agent
```

### 7.2 AgentCard 查询入口

```http
GET /a2a/{agentName}/card
```

例如：

```http
GET /a2a/order-agent/card
```

说明：

- `/card` 主要用于本地调试和直连查询
- AgentScope 的 Nacos A2A 发现核心依赖的是 Nacos 里的 `AgentCard` / `AgentEndpoint`
- 不是依赖这个 HTTP `/card` 接口完成发现

---

## 8. 注册到 Nacos 后的效果

每个 Agent 注册后，Nacos A2A 注册中心里会有两类信息：

### 8.1 AgentCard

包含：

- `agentName`
- `description`
- `version`
- `url`
- `preferredTransport`
- `skills`

其中：

- `url` 会被注册成类似：

```text
http://127.0.0.1:8083/a2a/order-agent
```

### 8.2 AgentEndpoint

包含：

- `address`
- `port`
- `path`
- `transport`
- `supportTls`
- `version`

例如：

- `address = 127.0.0.1`
- `port = 8083`
- `path = /a2a/order-agent`
- `transport = JSONRPC`

---

## 9. Demo 说明

当前 demo 应用提供了两个示例 Agent。

### 9.1 order-agent

- 业务接口：`POST /orders`
- A2A 入口：`POST /a2a/order-agent`
- AgentCard 查询：`GET /a2a/order-agent/card`

### 9.2 order-agent2

- 业务接口：`POST /orders2`
- A2A 入口：`POST /a2a/order-agent2`
- AgentCard 查询：`GET /a2a/order-agent2/card`

---

## 10. 最小接入步骤

### 第一步：项目已有 Nacos Discovery

确保业务项目已经接入：

- `spring-cloud-starter-alibaba-nacos-discovery`

### 第二步：引入 starter

```xml
<dependency>
    <groupId>com.nacosa2a</groupId>
    <artifactId>a2a-agent-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 第三步：补充配置

```properties
spring.application.name=demo-agent-app
server.port=8083

spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
spring.cloud.nacos.discovery.namespace=public
spring.cloud.nacos.discovery.group=DEFAULT_GROUP
spring.cloud.nacos.discovery.ip=127.0.0.1

a2a.registration.enabled=true
a2a.registration.preferred-transport=JSONRPC
a2a.registration.register-as-latest=true
a2a.registration.enabled-register-endpoint=true
a2a.registration.support-tls=false
```

### 第四步：在 Controller 方法上加 `@A2AAgent`

### 第五步：启动应用

启动后就会自动注册到 Nacos A2A。

---

## 11. 构建

```bash
mvn -DskipTests package
```

---

## 12. 说明

如果你的目标只是把应用实例注册到 Nacos 服务列表中，那么普通的 Nacos Discovery 已经够用。

如果你的目标是让 AgentScope 通过 Nacos 正常发现 Agent，并读取完整 AgentCard 和 Endpoint，则需要当前这套 A2A 注册能力。