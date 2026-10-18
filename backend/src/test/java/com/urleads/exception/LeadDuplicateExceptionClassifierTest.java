package com.urleads.exception;

import jakarta.persistence.RollbackException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("C06: Pruebas unitarias para LeadDuplicateExceptionClassifier")
class LeadDuplicateExceptionClassifierTest {

    @Test
    @DisplayName("Identifica restricción de correo normalizado en ConstraintViolationException")
    void extractDuplicateConstraintName_correoConstraint_retornaNombreCanonica() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(cve))
                .isEqualTo(LeadDuplicateExceptionClassifier.CONSTRAINT_CORREO);
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(cve))
                .isEqualTo(LeadDuplicateExceptionClassifier.MESSAGE_CORREO);
        assertThat(LeadDuplicateExceptionClassifier.isDuplicateConstraintViolation(cve))
                .isTrue();
    }

    @Test
    @DisplayName("Identifica restricción de teléfono normalizado en ConstraintViolationException")
    void extractDuplicateConstraintName_telefonoConstraint_retornaNombreCanonica() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "UQ_LEAD_OWNER_TELEFONO_NORM"
        );

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(cve))
                .isEqualTo(LeadDuplicateExceptionClassifier.CONSTRAINT_TELEFONO);
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(cve))
                .isEqualTo(LeadDuplicateExceptionClassifier.MESSAGE_TELEFONO);
        assertThat(LeadDuplicateExceptionClassifier.isDuplicateConstraintViolation(cve))
                .isTrue();
    }

    @Test
    @DisplayName("Identifica restricción envuelta en DataIntegrityViolationException directa de repositorio")
    void extractDuplicateConstraintName_envueltoEnDataIntegrityViolationException() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Error en BD", cve);

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(dive))
                .isEqualTo(LeadDuplicateExceptionClassifier.CONSTRAINT_CORREO);
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(dive))
                .isEqualTo(LeadDuplicateExceptionClassifier.MESSAGE_CORREO);
    }

    @Test
    @DisplayName("Identifica restricción en fallo diferido de commit de transacción (TransactionSystemException)")
    void extractDuplicateConstraintName_falloDiferidoEnCommitDeTransaccion() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_telefono_norm"
        );
        RollbackException rollbackEx = new RollbackException("Transaction marked as rollback only", cve);
        TransactionSystemException tse = new TransactionSystemException("Could not commit JPA transaction", rollbackEx);

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(tse))
                .isEqualTo(LeadDuplicateExceptionClassifier.CONSTRAINT_TELEFONO);
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(tse))
                .isEqualTo(LeadDuplicateExceptionClassifier.MESSAGE_TELEFONO);
    }

    @Test
    @DisplayName("Falla cerrado y retorna null ante restricción ajena de FK (ej. fk_lead_usuario)")
    void extractDuplicateConstraintName_restriccionAjenaFk_retornaNull() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "violates foreign key constraint",
                new SQLException("fk error"),
                "fk_lead_usuario"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("FK error", cve);

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.isDuplicateConstraintViolation(dive)).isFalse();
    }

    @Test
    @DisplayName("Falla cerrado y retorna null ante restricción ajena de CHECK (ej. chk_lead_nombre)")
    void extractDuplicateConstraintName_restriccionAjenaCheck_retornaNull() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "check constraint violation",
                new SQLException("check error"),
                "chk_lead_nombre"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Check error", cve);

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(dive)).isNull();
    }

    @Test
    @DisplayName("Falla cerrado y retorna null ante restricción de otra tabla (ej. uq_usuario_username)")
    void extractDuplicateConstraintName_restriccionAjenaOtraTabla_retornaNull() {
        ConstraintViolationException cve = new ConstraintViolationException(
                "unique constraint violation",
                new SQLException("unique error"),
                "uq_usuario_username"
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("User error", cve);

        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(dive)).isNull();
    }

    @Test
    @DisplayName("Falla cerrado ante excepción genérica sin metadata estructurada")
    void extractDuplicateConstraintName_sinMetadataEstructurada_retornaNull() {
        DataIntegrityViolationException dive = new DataIntegrityViolationException("arbitrary message with no cve");
        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.resolveDuplicateMessage(dive)).isNull();
        assertThat(LeadDuplicateExceptionClassifier.extractDuplicateConstraintName(null)).isNull();
    }
}
