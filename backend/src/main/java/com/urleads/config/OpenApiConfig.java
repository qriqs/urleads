package com.urleads.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Configuración de OpenAPI 3 / Swagger UI para UrLeads CRM (Backend V2 C05).
 * Modela esquemas de seguridad basados en cookies de sesión (JSESSIONID),
 * cabecera CSRF (X-XSRF-TOKEN) para mutaciones web, y Bearer JWT RS256
 * para consulta interna de microservicios con alcance crm.leads.read.
 */
@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH_SCHEME = "cookieAuth";
    public static final String CSRF_HEADER_SCHEME = "csrfToken";
    public static final String BEARER_JWT_SCHEME = "bearerJwt";

    @Bean
    public OpenApiCustomizer agendaOpenApiCustomizer(RequestMappingHandlerMapping mapping) {
        return openApi -> mapping.getHandlerMethods().forEach((info, handler) -> {
            String route = info.getPatternValues().stream().findFirst().orElse(null);
            if (route == null || !route.startsWith("/api/v1/")) return;
            PathItem item = openApi.getPaths().computeIfAbsent(route, ignored -> new PathItem());
            Operation operation = new Operation().summary(handler.getMethod().getName()).addTagsItem("Agenda");
            info.getMethodsCondition().getMethods().stream().findFirst().ifPresent(method -> {
                switch (method) {
                    case GET -> item.setGet(operation);
                    case POST -> item.setPost(operation);
                    case PUT -> item.setPut(operation);
                    case PATCH -> item.setPatch(operation);
                    case DELETE -> item.setDelete(operation);
                    default -> { }
                }
            });
        });
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UrLeads CRM REST API")
                        .version("0.0.1")
                        .description("Documentación oficial de los contratos REST de UrLeads CRM (Backend V2). "
                                + "Incluye endpoints públicos (/api/health, /api/auth/csrf, /api/auth/login), "
                                + "rutas protegidas por sesión web y CSRF (/api/**), "
                                + "emisión de tokens de acceso JWT (/api/auth/access-token), "
                                + "y consulta interna entre servicios protegida por Bearer JWT RS256 (/internal/v1/leads/{id}).")
                        .contact(new Contact().name("UrLeads Team")))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH_SCHEME, new SecurityScheme()
                                .name("JSESSIONID")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("Cookie de sesión HttpOnly establecida tras autenticación exitosa en POST /api/auth/login."))
                        .addSecuritySchemes(CSRF_HEADER_SCHEME, new SecurityScheme()
                                .name("X-XSRF-TOKEN")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN. "
                                        + "Requerido en el header X-XSRF-TOKEN para métodos mutantes (POST, PUT, DELETE) sobre /api/**."))
                        .addSecuritySchemes(BEARER_JWT_SCHEME, new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token Bearer JWT RS256 para comunicación interna entre microservicios (Backend V2 C02/C03). "
                                        + "Emisor requerido: urleads-crm, Audiencia requerida: crm-api, Scope requerido: crm.leads.read."))
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Autenticación de las rutas de Agenda conservadas de main.")));
    }

    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) {
                return;
            }

            // Documentar explícitamente POST /api/auth/logout gestionado por Spring Security Filter
            if (!paths.containsKey("/api/auth/logout")) {
                PathItem logoutItem = new PathItem();
                Operation logoutOp = new Operation()
                        .addTagsItem("Autenticación")
                        .summary("Cerrar sesión")
                        .description("Invalida la sesión web actual, revoca el contexto de seguridad y elimina la cookie JSESSIONID. Requiere sesión web activa (cookie JSESSIONID) y cabecera obligatoria X-XSRF-TOKEN con el token CSRF.")
                        .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME))
                        .addParametersItem(new io.swagger.v3.oas.models.parameters.HeaderParameter()
                                .name("X-XSRF-TOKEN")
                                .required(true)
                                .description("Token CSRF obtenido de GET /api/auth/csrf o de la cookie XSRF-TOKEN."))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("Sesión cerrada exitosamente (sin contenido)"))
                                .addApiResponse("401", new ApiResponse().description("Sin sesión activa o no autorizado")));
                logoutItem.post(logoutOp);
                paths.addPathItem("/api/auth/logout", logoutItem);
            }
        };
    }
}
