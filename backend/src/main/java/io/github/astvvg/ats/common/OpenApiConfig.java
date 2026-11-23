package io.github.astvvg.ats.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI atsOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Mini ATS API")
                                .version("v1")
                                .description(
                                        "REST API for managing job openings, candidates and the hiring pipeline."));
    }
}
