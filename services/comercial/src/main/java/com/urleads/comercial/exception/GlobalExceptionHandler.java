package com.urleads.comercial.exception;

import com.urleads.comercial.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();
        String code = "VALIDATION_ERROR";
        String message = "Error de validación en la solicitud.";

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ApiErrorResponse.FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
            if ("unitPrice".equals(fe.getField())) {
                code = "INVALID_PRICE";
                message = "El precio unitario debe ser un valor decimal positivo.";
            } else if ("currency".equals(fe.getField()) && !"INVALID_PRICE".equals(code)) {
                code = "INVALID_CURRENCY";
                message = "La moneda debe ser PEN o USD (ISO 4217).";
            }
        }

        ApiErrorResponse response = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), code, message, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DuplicateCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCode(DuplicateCodeException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.CONFLICT.value(),
            "DUPLICATE_CODE",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "BAD_REQUEST",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_STATE",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.urleads.comercial.client.exception.CrmUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleCrmUnavailable(com.urleads.comercial.client.exception.CrmUnavailableException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "CRM_UNAVAILABLE",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(com.urleads.comercial.client.exception.CrmForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleCrmForbidden(com.urleads.comercial.client.exception.CrmForbiddenException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "FORBIDDEN",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(com.urleads.comercial.client.exception.CrmAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleCrmAuthentication(com.urleads.comercial.client.exception.CrmAuthenticationException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "UNAUTHORIZED",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}
