package com.urleads.comercial.entity;

public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED;

    public boolean isFinal() {
        return this == ACCEPTED || this == REJECTED;
    }

    public boolean canTransitionTo(QuoteStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true; // Idempotente
        }
        return switch (this) {
            case DRAFT -> target == SENT || target == ACCEPTED || target == REJECTED;
            case SENT -> target == ACCEPTED || target == REJECTED;
            case ACCEPTED, REJECTED -> false;
        };
    }
}
