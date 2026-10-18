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
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerResourceResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.util.List;

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
                                        + "Emisor requerido: urleads-crm, Audiencia requerida: crm-api, Scope requerido: crm.leads.read.")));
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

    /**
     * Controlador para servir recursos estáticos de Swagger UI asegurando prioridad sobre el catch-all SPA.
     * Delega la resolución a la cadena de recursos oficial de Springdoc registrada en resourceHandlerMapping.
     */
    @Controller
    @RequestMapping("/swagger-ui")
    public static class SwaggerUiResourceController {

        private final org.springframework.web.servlet.HandlerMapping resourceHandlerMapping;

        public SwaggerUiResourceController(
                @org.springframework.beans.factory.annotation.Qualifier("resourceHandlerMapping")
                org.springframework.web.servlet.HandlerMapping resourceHandlerMapping) {
            this.resourceHandlerMapping = resourceHandlerMapping;
        }

        @GetMapping("/{*resource}")
        public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
            org.springframework.web.servlet.HandlerExecutionChain chain = resourceHandlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof org.springframework.web.HttpRequestHandler httpRequestHandler) {
                httpRequestHandler.handleRequest(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
}
