package com.urleads.entity;

public enum LeadStage {

    NUEVO,
    EN_SEGUIMIENTO,
    CERRADO;

    public static boolean isValid(String stage) {
        if (stage == null) {
            return false;
        }
        for (LeadStage s : values()) {
            if (s.name().equalsIgnoreCase(stage.trim())) {
                return true;
            }
        }
        return false;
    }
}