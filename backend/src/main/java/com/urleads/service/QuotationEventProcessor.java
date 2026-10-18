package com.urleads.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.event.QuotationAcceptedData;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.entity.Notification;
import com.urleads.entity.ProcessedEvent;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.ProcessedEventRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class QuotationEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuotationEventProcessor.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public QuotationEventProcessor(
            @Autowired(required = false) ProcessedEventRepository processedEventRepository,
            @Autowired(required = false) NotificationRepository notificationRepository,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) NotificationWebSocketHandler webSocketHandler,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.usuarioRepository = usuarioRepository;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Persiste atómicamente el evento procesado en la tabla de deduplicación
     * y genera la notificación en PostgreSQL.
     */
    @Transactional
    public Notification procesarEventoCotizacionAceptada(QuotationAcceptedEvent event) {
        String eventId = event.getEventId();

        // 1. Registrar en tabla de deduplicación
        if (processedEventRepository != null) {
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(event.getEventType() != null ? event.getEventType() : "quotation.accepted.v1")
                    .processedAt(Instant.now())
                    .build();
            processedEventRepository.save(processedEvent);
        }

        // 2. Resolver usuario destinatario
        Usuario usuario = resolverUsuario(event.getOwnerId());

        // 3. Construir mensaje y datos
        QuotationAcceptedData data = event.getData();
        Long quoteId = data != null ? data.getQuoteId() : null;
        Long leadId = data != null ? data.getLeadId() : null;
        String currency = (data != null && data.getCurrency() != null) ? data.getCurrency() : "USD";
        BigDecimal total = (data != null && data.getTotal() != null) ? data.getTotal() : BigDecimal.ZERO;

        String titulo = "Cotización #" + (quoteId != null ? quoteId : "N/A") + " aceptada";
        String mensaje = String.format("La cotización #%s para el lead #%s fue aceptada por un monto de %s %.2f.",
                quoteId != null ? quoteId : "-",
                leadId != null ? leadId : "-",
                currency,
                total);

        String jsonMetadata = null;
        try {
            jsonMetadata = objectMapper.writeValueAsString(data != null ? data : event);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar data a JSON para evento {}: {}", eventId, e.getMessage());
        }

        // 4. Persistir Notificación en PostgreSQL
        Notification notification = Notification.builder()
                .usuario(usuario)
                .eventId(eventId)
                .tipo("COTIZACION_ACEPTADA")
                .titulo(titulo)
                .mensaje(mensaje)
                .data(jsonMetadata)
                .leida(false)
                .creadoEn(event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now())
                .build();

        Notification guardada = notification;
        if (notificationRepository != null) {
            guardada = notificationRepository.save(notification);
        }

        // 5. Emitir en tiempo real por WebSocket si el usuario está conectado
        if (webSocketHandler != null && usuario != null) {
            webSocketHandler.sendNotificationToUser(usuario.getId(), NotificationResponse.fromEntity(guardada));
        }

        return guardada;
    }

    private Usuario resolverUsuario(String ownerId) {
        if (usuarioRepository == null) {
            return null;
        }

        if (ownerId != null && !ownerId.isBlank()) {
            // Si el ownerId es numérico (ID de usuario decimal)
            if (ownerId.matches("\\d+")) {
                try {
                    Long id = Long.parseLong(ownerId);
                    var userOpt = usuarioRepository.findById(id);
                    if (userOpt.isPresent()) {
                        return userOpt.get();
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Si es username
            var userByUsername = usuarioRepository.findByUsername(ownerId);
            if (userByUsername.isPresent()) {
                return userByUsername.get();
            }
        }

        // Fallback al usuario único configurado en UrLeads
        return usuarioRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró usuario configurado para asociar la notificación del evento"));
    }
}
