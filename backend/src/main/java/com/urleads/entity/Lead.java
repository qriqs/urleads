package com.urleads.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "lead")
@Data
public class Lead {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	private String nombre;

	private String telefono;

	private String correo;

	private String etapa;

	@Column(name = "proximo_seguimiento")
	private LocalDate proximoSeguimiento;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private OffsetDateTime actualizadoEn;

	@OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Nota> notas;
	
	@Column(name = "service")
	private String service;

	@PrePersist
	public void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		if (this.creadoEn == null) {
			this.creadoEn = now;
		}
		if (this.actualizadoEn == null) {
			this.actualizadoEn = now;
		}
	}

	@PreUpdate
	public void preUpdate() {
		this.actualizadoEn = OffsetDateTime.now();
	}
}