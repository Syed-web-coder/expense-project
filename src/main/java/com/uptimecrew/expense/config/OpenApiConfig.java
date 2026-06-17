package com.uptimecrew.expense.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Pattern reference for Task 1 (springdoc-openapi).
//
// Declaring a SecurityScheme of type HTTP/bearer makes Swagger UI render an
// "Authorize" button that sends the JWT on every protected request, so
// engineers can probe the secured /api/v1/** routes from the in-browser UI.
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI expenseOpenApi() {
        final String schemeName = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("Merchants API")
                        .version("v1.0.0")
                        .description("REST API for the Merchants bounded context. "
                                + "All endpoints require a Bearer JWT with the appropriate scope and role."))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
