package com.urleads.comercial.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI comercialOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("UrLeads - Servicio Comercial API")
                .description("API REST del Microservicio Comercial (Backend V2) para gestión de catálogo de productos/servicios, " +
                             "cotizaciones con cálculo monetario exacto, plantillas reutilizables, ciclo de vida de cotizaciones, " +
                             "control inmutable de versiones/revisiones, generación idempotente de órdenes y métricas agregadas por moneda.")
                .version("v2.0")
                .contact(new Contact()
                    .name("Equipo Backend UrLeads")
                    .email("soporte@urleads.com")))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Token JWT emitido por CRM con algoritmo RS256, audiencia 'comercial-api' y scope 'comercial.access'.")));
    }
}
