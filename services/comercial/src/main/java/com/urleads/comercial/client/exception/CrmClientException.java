package com.urleads.comercial.client.exception;

public class CrmClientException extends RuntimeException {
    public CrmClientException(String message) {
        super(message);
    }

    public CrmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
