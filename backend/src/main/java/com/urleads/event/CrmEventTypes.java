package com.urleads.event;

/**
 * Constantes canónicas para el intercambio y tipos de eventos de dominio CRM (Backend V2 C04).
 */
public final class CrmEventTypes {

    public static final String EXCHANGE = "urleads.events.v1";

    public static final String LEAD_CREATED = "lead.created.v1";
    public static final String LEAD_UPDATED = "lead.updated.v1";
    public static final String LEAD_DELETED = "lead.deleted.v1";

    private CrmEventTypes() {
    }
}
