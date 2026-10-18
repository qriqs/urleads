package com.urleads.entity;

public final class OutboxStatus {
    public static final String PENDING = "PENDING";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED = "FAILED";

    private OutboxStatus() {
    }
}
