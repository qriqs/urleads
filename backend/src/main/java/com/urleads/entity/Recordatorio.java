package com.urleads.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "recordatorio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurrence_id", nullable = false, unique = true, length = 64)
    private String occurrenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType; // CITA, TAREA, LEAD

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "programado_para", nullable = false)
    private Instant programadoPara;

    @Column(name = "estado", nullable = false, length = 32)
    @Builder.Default
    private String estado = "PENDIENTE"; // PENDIENTE, PROCESADO, CANCELADO, FALLIDO

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();
}
