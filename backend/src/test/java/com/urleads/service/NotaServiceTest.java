package com.urleads.service;

import com.urleads.dto.NotaRequest;
import com.urleads.dto.NotaResponse;
import com.urleads.entity.Lead;
import com.urleads.entity.Nota;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DSW-52 C12: Pruebas unitarias de aislamiento y exclusión de leads archivados en NotaService")
class NotaServiceTest {

    @Mock
    private NotaRepository notaRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private NotaService notaService;

    private Usuario usuario;
    private Lead leadActivo;
    private Lead leadArchivado;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("testuser");

        leadActivo = new Lead();
        leadActivo.setId(10L);
        leadActivo.setUsuario(usuario);
        leadActivo.setNombre("Lead Activo");

        leadArchivado = new Lead();
        leadArchivado.setId(20L);
        leadArchivado.setUsuario(usuario);
        leadArchivado.setNombre("Lead Archivado");
        leadArchivado.setArchivedAt(OffsetDateTime.parse("2026-10-18T10:00:00Z"));
    }

    @Test
    @DisplayName("listarNotas: lead activo retorna lista de notas exitosamente")
    void listarNotas_leadActivo_retornaNotas() {
        when(usuarioRepository.findByUsername("testuser")).thenReturn(Optional.of(usuario));
        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(leadActivo));

        Nota nota = new Nota();
        nota.setId(100L);
        nota.setLead(leadActivo);
        nota.setContenido("Primera nota");
        nota.setCreadoEn(OffsetDateTime.now());

        when(notaRepository.findByLeadIdOrderByCreadoEnAsc(10L)).thenReturn(List.of(nota));

        List<NotaResponse> notas = notaService.listarNotas(10L, "testuser");

        assertThat(notas).hasSize(1);
        assertThat(notas.get(0).content()).isEqualTo("Primera nota");
        verify(notaRepository).findByLeadIdOrderByCreadoEnAsc(10L);
    }

    @Test
    @DisplayName("listarNotas: lead archivado lanza ResourceNotFoundException (404)")
    void listarNotas_leadArchivado_lanzaResourceNotFound() {
        when(usuarioRepository.findByUsername("testuser")).thenReturn(Optional.of(usuario));
        when(leadRepository.findByIdAndUsuarioId(20L, 1L)).thenReturn(Optional.of(leadArchivado));

        assertThatThrownBy(() -> notaService.listarNotas(20L, "testuser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(notaRepository, never()).findByLeadIdOrderByCreadoEnAsc(any());
    }

    @Test
    @DisplayName("crearNota: lead activo persiste y retorna nota creada")
    void crearNota_leadActivo_persisteNota() {
        when(usuarioRepository.findByUsername("testuser")).thenReturn(Optional.of(usuario));
        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(leadActivo));

        Nota notaGuardada = new Nota();
        notaGuardada.setId(101L);
        notaGuardada.setLead(leadActivo);
        notaGuardada.setContenido("Nota nueva");
        notaGuardada.setCreadoEn(OffsetDateTime.now());

        when(notaRepository.save(any(Nota.class))).thenReturn(notaGuardada);

        NotaResponse response = notaService.crearNota(10L, new NotaRequest("Nota nueva"), "testuser");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.content()).isEqualTo("Nota nueva");
        verify(notaRepository).save(any(Nota.class));
    }

    @Test
    @DisplayName("crearNota: lead archivado lanza ResourceNotFoundException (404)")
    void crearNota_leadArchivado_lanzaResourceNotFound() {
        when(usuarioRepository.findByUsername("testuser")).thenReturn(Optional.of(usuario));
        when(leadRepository.findByIdAndUsuarioId(20L, 1L)).thenReturn(Optional.of(leadArchivado));

        assertThatThrownBy(() -> notaService.crearNota(20L, new NotaRequest("Nota no permitida"), "testuser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(notaRepository, never()).save(any());
    }
}
