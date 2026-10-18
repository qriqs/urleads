package com.urleads.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.event.QuotationAcceptedData;
import com.urleads.dto.event.QuotationAcceptedEvent;
import com.urleads.entity.Notification;
import com.urleads.entity.ProcessedEvent;
import com.urleads.entity.Usuario;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.ProcessedEventRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationEventProcessorTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificationWebSocketHandler webSocketHandler;

    private ObjectMapper objectMapper;
    private QuotationEventProcessor processor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        processor = new QuotationEventProcessor(
                processedEventRepository,
                notificationRepository,
                usuarioRepository,
                webSocketHandler,
                objectMapper
        );
    }

    @Test
    @DisplayName("Procesa evento atómicamente: guarda evento procesado, notificación y emite por WebSocket")
    void procesarEvento_guardaEnBdyNotifica() {
        Usuario usuario = new Usuario(42L, "admin", "hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));

        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-test-999")
                .eventType("quotation.accepted.v1")
                .version(1)
                .occurredAt(Instant.now())
                .ownerId("42")
                .data(QuotationAcceptedData.builder()
                        .quoteId(204L)
                        .leadId(105L)
                        .currency("USD")
                        .total(new BigDecimal("1250.00"))
                        .build())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(10L);
            return n;
        });

        Notification resultado = processor.procesarEventoCotizacionAceptada(event);

        assertNotNull(resultado);
        assertEquals("Cotización #204 aceptada", resultado.getTitulo());
        assertEquals("COTIZACION_ACEPTADA", resultado.getTipo());
        assertEquals("evt-test-999", resultado.getEventId());

        // Verifica guardado en processed_event (deduplicación)
        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        assertEquals("evt-test-999", eventCaptor.getValue().getEventId());
        assertEquals("quotation.accepted.v1", eventCaptor.getValue().getEventType());

        // Verifica guardado en notification
        verify(notificationRepository).save(any(Notification.class));

        // Verifica emisión por WebSocket
        verify(webSocketHandler).sendNotificationToUser(eq(42L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("Resolución de usuario con UUID fallback: encuentra usuario configurado único")
    void procesarEvento_conUuidOwnerId_usaUsuarioFallback() {
        Usuario fallbackUser = new Usuario(1L, "vendedor", "hash");
        when(usuarioRepository.findByUsername("uuid-comercial-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findAll()).thenReturn(List.of(fallbackUser));

        QuotationAcceptedEvent event = QuotationAcceptedEvent.builder()
                .eventId("evt-fallback-1")
                .ownerId("uuid-comercial-123")
                .data(QuotationAcceptedData.builder().quoteId(100L).total(BigDecimal.TEN).build())
                .build();

        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification resultado = processor.procesarEventoCotizacionAceptada(event);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getUsuario().getId());
    }
}
