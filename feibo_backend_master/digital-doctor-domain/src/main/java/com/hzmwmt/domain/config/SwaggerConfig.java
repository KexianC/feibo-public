package com.hzmwmt.domain.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // 关键：明确指定 OpenAPI 版本（必须是 3.x.y 格式）
                .openapi("3.1.0")
                // 补充文档信息（标题、版本等，不影响核心版本检测）
                .info(new Info()
                        .title("项目 API 文档")
                        .version("1.0.0") // 这里是接口版本，不是 OpenAPI 规范版本
                        .description("基于 OpenAPI 3.0 的接口文档"));
    }
}