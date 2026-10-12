package com.urleads.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(
            LeadNotFoundException.class)
    public ResponseEntity<ErrorResponse>
            handleLeadNotFound(
                    LeadNotFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        new ErrorResponse(
                                404,
                                "NOT_FOUND",
                                ex.getMessage()));
    }
    @ExceptionHandler(
            MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>>
            handleValidation(
                    MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors =
                new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fieldErrors.put(
                                error.getField(),
                                error.getDefaultMessage()));

        Map<String, Object> response =
                new HashMap<>();

        response.put("status", 400);
        response.put("code", "VALIDATION_ERROR");
        response.put(
                "message",
                "Revisa los campos indicados.");
        response.put(
                "fieldErrors",
                fieldErrors);

        return ResponseEntity
                .badRequest()
                .body(response);
    }
    @ExceptionHandler(
            IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse>
            handleIllegalArgument(
                    IllegalArgumentException ex) {

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        400,
                        "VALIDATION_ERROR",
                        ex.getMessage()));
    }
}