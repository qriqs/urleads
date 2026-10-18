package com.urleads.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UrLeads - Servicio Agenda y Notificaciones API")
                        .version("v2.0.0")
                        .description("Documentación OpenAPI 3 del microservicio Agenda y Notificaciones (Alex). "
                                + "Incluye operaciones REST para notificaciones (/api/v1/notifications), tareas (/api/v1/tasks), "
                                + "citas (/api/v1/appointments), minutas (/api/v1/appointments/{id}/outcome), "
                                + "feed combinado (/api/v1/agenda, /api/v1/agenda/daily-pending) y preferencias (/api/v1/preferences).")
                        .contact(new Contact().name("Alex - Agenda & Notificaciones").email("alex@urleads.com"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .schemaRequirement("BearerAuth", new SecurityScheme()
                        .name("BearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT con claim sub (usuario.id) y scope agenda.access"));
    }
}
