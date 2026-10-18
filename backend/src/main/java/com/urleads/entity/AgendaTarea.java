package com.urleads.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "agenda_tarea")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgendaTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @Column(name = "hora_limite")
    private LocalTime horaLimite;

    @Column(name = "estado", nullable = false, length = 32)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private Instant actualizadoEn = Instant.now();
}
