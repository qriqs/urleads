package com.urleads.exception;

public class LeadValidationException
        extends RuntimeException {

    public LeadValidationException(String message) {
        super(message);
    }
}