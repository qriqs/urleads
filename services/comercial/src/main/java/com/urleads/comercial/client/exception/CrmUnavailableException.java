package com.urleads.comercial.client.exception;

public class CrmUnavailableException extends CrmClientException {
    public CrmUnavailableException(String message) {
        super(message);
    }

    public CrmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
