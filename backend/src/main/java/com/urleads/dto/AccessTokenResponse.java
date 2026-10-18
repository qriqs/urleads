package com.urleads.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para la respuesta de emisión de token de acceso JWT (Backend V2 C02).
 * Formato estándar según RFC 6749 y especificación de contratos Backend V2.
 */
public record AccessTokenResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn
) {
}
