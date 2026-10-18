package com.urleads.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "agenda_cita_minuta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgendaCitaMinuta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false, unique = true)
    private AgendaCita cita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "resumen", nullable = false, columnDefinition = "TEXT")
    private String resumen;

    @Column(name = "acuerdos", columnDefinition = "TEXT")
    private String acuerdos;

    @Column(name = "proximas_acciones", columnDefinition = "TEXT")
    private String proximasAcciones;

    @Column(name = "resultado", nullable = false, length = 64)
    @Builder.Default
    private String resultado = "REALIZADA";

    @Column(name = "asistencia", length = 64)
    private String asistencia;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private Instant actualizadoEn = Instant.now();
}
