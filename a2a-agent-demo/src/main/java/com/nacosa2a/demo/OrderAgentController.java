package com.nacosa2a.demo;

import com.nacosa2a.core.annotation.A2AAgent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供 demo 场景的下单接口，验证注解扫描、注册和 A2A 协议入口的端到端流程。
 */
@RestController
public class OrderAgentController {

    /**
     * 接收下单请求并返回 demo 响应。
     */
    @PostMapping("/orders")
    @A2AAgent(name = "order-agent", description = "创建订单")
    public Map<String, Object> createOrder(@RequestBody OrderRequest request) {
        return Map.of(
                "agent", "order-agent",
                "drinkName", request.drinkName(),
                "size", request.size()
        );
    }

    /**
     * 接收下单请求并返回 demo 响应。
     */
    @PostMapping("/orders2")
    @A2AAgent(name = "order-agent2", description = "创建订单")
    public Map<String, Object> fixOrder(@RequestBody OrderRequest request) {
        return Map.of(
                "agent", "order-agent",
                "drinkName", request.drinkName(),
                "size", request.size()
        );
    }

    public record OrderRequest(
            // 饮品名称
            String drinkName,
            // 规格
            String size) {
    }


}
