package com.urleads.comercial.dto;

import java.util.List;

public record ApiErrorResponse(
    int status,
    String code,
    String message,
    List<FieldErrorItem> fieldErrors
) {
    public ApiErrorResponse(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public record FieldErrorItem(String field, String message) {}
}
