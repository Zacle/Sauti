package com.sauti.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI sautiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sauti API")
                        .version("0.1.0")
                        .description("Backend API for the Sauti multilingual voice agent platform."));
    }
}
