package com.urleads.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidLeadAssociationException extends RuntimeException {
    public InvalidLeadAssociationException(String message) {
        super(message);
    }
}
