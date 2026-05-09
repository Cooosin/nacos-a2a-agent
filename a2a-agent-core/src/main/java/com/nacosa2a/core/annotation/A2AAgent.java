package com.nacosa2a.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface A2AAgent {

    /**
     * 声明 Agent 名称。
     */
    String name();

    /**
     * 声明 Agent 描述。
     */
    String description() default "";

    /**
     * 声明 Agent 版本。
     */
    String version() default "1.0.0";

    /**
     * 声明 Agent 自定义访问地址，未配置时按名称生成默认地址。
     */
    String endpoint() default "";

    /**
     * 声明 Agent 标签。
     */
    String[] tags() default {};

    /**
     * 声明 Agent 扩展元数据，按 key=value 形式提供，后续流程再转换为 Map 模型。
     */
    String[] metadata() default {};
}
