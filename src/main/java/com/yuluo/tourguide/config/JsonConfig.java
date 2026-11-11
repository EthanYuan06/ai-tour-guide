package com.yuluo.tourguide.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring MVC Json 配置
 */
@Configuration
public class JsonConfig {

    /**
     * Long 转 String 序列化模块，规避前端 JS Number 精度丢失（超 2^53 的 Long id 会被舍入）
     * Spring Boot 会自动把容器中的 Module Bean 注册到 ObjectMapper，无需替换整个 ObjectMapper
     */
    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}