package com.urleads.service;

import java.util.Locale;

/**
 * Normalizador determinista de medios de contacto (correo y teléfono) para
 * detección de duplicados por propietario (DSW-46 / C06).
 */
public final class LeadNormalizer {

    private LeadNormalizer() {
    }

    /**
     * Normaliza un correo electrónico:
     * - Si es null o solo contiene espacios en blanco, retorna null (ausente).
     * - Recorta espacios iniciales y finales y convierte a minúsculas canónicas.
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Normaliza un número telefónico extrayendo exclusivamente dígitos ASCII ('0'-'9'):
     * - Si es null, solo contiene espacios o no contiene ningún dígito ASCII, retorna null (ausente).
     * - Remueve puntuación, espacios y caracteres no ASCII (alineado con PostgreSQL [^0-9]).
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        if (sb.isEmpty()) {
            return null;
        }
        return sb.toString();
    }
}
