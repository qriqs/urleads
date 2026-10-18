package com.urleads.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "zona_horaria", nullable = false, length = 64)
    @Builder.Default
    private String zonaHoraria = "America/Lima";

    @Column(name = "inicio_jornada", nullable = false)
    @Builder.Default
    private LocalTime inicioJornada = LocalTime.of(9, 0);

    @Column(name = "fin_jornada", nullable = false)
    @Builder.Default
    private LocalTime finJornada = LocalTime.of(18, 0);

    @Column(name = "dias_laborales", nullable = false, length = 64)
    @Builder.Default
    private String diasLaborales = "1,2,3,4,5";

    @Column(name = "notificaciones_habilitadas", nullable = false)
    @Builder.Default
    private Boolean notificacionesHabilitadas = true;

    @Column(name = "canal_websocket", nullable = false)
    @Builder.Default
    private Boolean canalWebsocket = true;

    @Column(name = "canal_email", nullable = false)
    @Builder.Default
    private Boolean canalEmail = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private Instant actualizadoEn = Instant.now();
}
