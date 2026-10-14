package com.urleads.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IMPLEMENTACIÓN TEMPORAL - BLOQUEO DE INTEGRACIÓN CON AUTENTICACIÓN.
 * El usuario se toma de configuración del servidor, nunca del cliente.
 * Reemplazar por una implementación que lea el SecurityContext cuando
 * la autenticación (Alex) esté disponible.
 */
@Component
public class TemporaryCurrentUserProvider implements CurrentUserProvider {

    @Value("${urleads.auth.temporary-user-id:51}")
    private Long temporaryUserId;

    @Override
    public Long getCurrentUserId() {
        return temporaryUserId;
    }
}