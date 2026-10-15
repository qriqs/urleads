package com.urleads.exception;

public class LeadValidationException extends IllegalArgumentException {

    public LeadValidationException(String message) {
        super(message);
    }
}