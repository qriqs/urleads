package com.urleads.exception;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Clasificador de excepciones de integridad de base de datos para
 * identificar exclusivamente las restricciones de duplicados de contacto de C06.
 *
 * Falla de forma cerrada (fail-closed): si no existe metadata estructurada que
 * coincida exactamente con las restricciones de C06, no se clasifica como duplicado.
 */
public final class LeadDuplicateExceptionClassifier {

    public static final String CONSTRAINT_CORREO = "uq_lead_owner_correo_norm";
    public static final String CONSTRAINT_TELEFONO = "uq_lead_owner_telefono_norm";

    public static final String MESSAGE_CORREO = "Ya existe un lead con este correo";
    public static final String MESSAGE_TELEFONO = "Ya existe un lead con este teléfono";

    private LeadDuplicateExceptionClassifier() {
    }

    /**
     * Inspecciona la cadena causal buscando metadata estructurada de Hibernate
     * o del driver de PostgreSQL que identifique la restricción.
     * Retorna el nombre canónico de la restricción si coincide con C06, o null.
     */
    public static String extractDuplicateConstraintName(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable current = throwable;
        while (current != null) {
            // 1. Metadata estructurada de Hibernate
            if (current instanceof ConstraintViolationException cve) {
                String constraint = normalizeConstraint(cve.getConstraintName());
                if (isDuplicateConstraint(constraint)) {
                    return constraint;
                }
            }

            // 2. Metadata estructurada de PostgreSQL ServerErrorMessage (reflexión defensiva)
            if ("org.postgresql.util.PSQLException".equals(current.getClass().getName())) {
                try {
                    java.lang.reflect.Method getMsg = current.getClass().getMethod("getServerErrorMessage");
                    Object serverError = getMsg.invoke(current);
                    if (serverError != null) {
                        java.lang.reflect.Method getConstraint = serverError.getClass().getMethod("getConstraint");
                        Object constraintObj = getConstraint.invoke(serverError);
                        if (constraintObj instanceof String s) {
                            String constraint = normalizeConstraint(s);
                            if (isDuplicateConstraint(constraint)) {
                                return constraint;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Ignora fallos de reflexión y continúa inspección de causas
                }
            }

            current = current.getCause();
        }

        return null;
    }

    public static boolean isDuplicateConstraintViolation(Throwable throwable) {
        return extractDuplicateConstraintName(throwable) != null;
    }

    public static String resolveDuplicateMessage(Throwable throwable) {
        String constraint = extractDuplicateConstraintName(throwable);
        if (CONSTRAINT_CORREO.equals(constraint)) {
            return MESSAGE_CORREO;
        }
        if (CONSTRAINT_TELEFONO.equals(constraint)) {
            return MESSAGE_TELEFONO;
        }
        return null;
    }

    private static boolean isDuplicateConstraint(String constraint) {
        return CONSTRAINT_CORREO.equals(constraint) || CONSTRAINT_TELEFONO.equals(constraint);
    }

    private static String normalizeConstraint(String constraint) {
        if (constraint == null) {
            return null;
        }
        return constraint.trim().toLowerCase(Locale.ROOT);
    }
}
