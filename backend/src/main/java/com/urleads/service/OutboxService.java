package com.urleads.service;

import com.urleads.entity.Lead;
import com.urleads.entity.OutboxEvent;

/**
 * Servicio para el registro de eventos de dominio CRM en la tabla outbox (Backend V2 C04).
 */
public interface OutboxService {

    OutboxEvent recordLeadCreated(Long usuarioId, Lead lead);

    OutboxEvent recordLeadUpdated(Long usuarioId, Lead lead);

    OutboxEvent recordLeadDeleted(Long usuarioId, Long leadId);
}
