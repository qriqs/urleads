package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.urleads.config.ClockConfig;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.TagResponse;
import com.urleads.entity.Lead;
import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.TagNotFoundException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.TagRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.LeadService;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTagsTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TagRepository tagRepository;

    private LeadService leadService;

    private static final Long USUARIO_ID = 42L;
    private static final Long LEAD_ID = 100L;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2026, 10, 18).atStartOfDay(ClockConfig.LIMA_ZONE).toInstant(),
                ClockConfig.LIMA_ZONE
        );
        leadService = new LeadServiceImpl(leadRepository, usuarioRepository, tagRepository, null, fixedClock);
    }

    @Test
    @DisplayName("actualizarTags: reemplaza el conjunto de etiquetas atómicamente")
    void actualizarTags_reemplazaEtiquetas() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Lead lead = new Lead();
        lead.setId(LEAD_ID);
        lead.setUsuario(usuario);
        lead.setNombre("Lead Test");
        lead.setEtapa("NUEVO");

        Tag t1 = new Tag(usuario, "VIP");
        t1.setId(1L);
        t1.setCreadoEn(OffsetDateTime.now());

        Tag t2 = new Tag(usuario, "Empresa");
        t2.setId(2L);
        t2.setCreadoEn(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(lead));
        when(tagRepository.findByUsuarioIdAndIdIn(eq(USUARIO_ID), any())).thenReturn(List.of(t1, t2));
        when(leadRepository.save(lead)).thenReturn(lead);

        LeadResponse response = leadService.actualizarTags(LEAD_ID, USUARIO_ID, List.of(1L, 2L, 1L)); // duplicados en request

        assertThat(response.getTags()).hasSize(2);
        assertThat(response.getTags().stream().map(TagResponse::name).toList())
                .containsExactlyInAnyOrder("Empresa", "VIP");
        verify(leadRepository).save(lead);
    }

    @Test
    @DisplayName("actualizarTags: lista vacía [] desasocia todas las etiquetas")
    void actualizarTags_vacioLimpiaEtiquetas() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Lead lead = new Lead();
        lead.setId(LEAD_ID);
        lead.setUsuario(usuario);
        lead.setNombre("Lead Test");
        lead.setEtapa("NUEVO");

        Tag t1 = new Tag(usuario, "VIP");
        t1.setId(1L);
        lead.setTags(new HashSet<>(Set.of(t1)));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(lead));
        when(leadRepository.save(lead)).thenReturn(lead);

        LeadResponse response = leadService.actualizarTags(LEAD_ID, USUARIO_ID, List.of());

        assertThat(response.getTags()).isEmpty();
        assertThat(lead.getTags()).isEmpty();
        verify(leadRepository).save(lead);
    }

    @Test
    @DisplayName("actualizarTags: si alguna etiqueta no existe o pertenece a otro usuario, lanza TagNotFoundException sin mutar")
    void actualizarTags_etiquetaAjenaOInexistente_lanzaTagNotFoundException() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Lead lead = new Lead();
        lead.setId(LEAD_ID);
        lead.setUsuario(usuario);
        lead.setNombre("Lead Test");
        lead.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(lead));
        // El usuario pidió etiquetas 1 y 999, pero sólo la 1 pertenece al usuario
        Tag t1 = new Tag(usuario, "VIP");
        t1.setId(1L);
        when(tagRepository.findByUsuarioIdAndIdIn(eq(USUARIO_ID), any())).thenReturn(List.of(t1));

        assertThatThrownBy(() -> leadService.actualizarTags(LEAD_ID, USUARIO_ID, List.of(1L, 999L)))
                .isInstanceOf(TagNotFoundException.class)
                .hasMessageContaining("no existen o no pertenecen");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizarTags: lead inexistente o de otro usuario lanza LeadNotFoundException")
    void actualizarTags_leadAjeno_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(999L, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.actualizarTags(999L, USUARIO_ID, List.of(1L)))
                .isInstanceOf(LeadNotFoundException.class);

        verify(tagRepository, never()).findByUsuarioIdAndIdIn(any(), any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("listarPaginado: con tagIds delega en buscarConTags y asocia etiquetas sin N+1")
    void listarPaginado_conFiltroTagIds() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Lead l1 = new Lead();
        l1.setId(10L);
        l1.setUsuario(usuario);
        l1.setNombre("Lead A");
        l1.setEtapa("NUEVO");

        Tag t1 = new Tag(usuario, "VIP");
        t1.setId(1L);
        l1.setTags(new HashSet<>(Set.of(t1)));

        Page<Lead> pageResult = new PageImpl<>(List.of(l1), PageRequest.of(0, 20), 1);

        when(leadRepository.buscarConTags(eq(USUARIO_ID), any(), any(), any(), any(), any(), any(), eq(List.of(1L, 2L)), any()))
                .thenReturn(pageResult);
        when(leadRepository.findAllWithTagsByIdIn(List.of(10L), USUARIO_ID)).thenReturn(List.of(l1));

        PageResponse<LeadResponse> response = leadService.listarPaginado(
                USUARIO_ID, null, null, null, List.of(1L, 2L), 0);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getTags()).hasSize(1);
        assertThat(response.content().get(0).getTags().get(0).name()).isEqualTo("VIP");
        verify(leadRepository).buscarConTags(eq(USUARIO_ID), any(), any(), any(), any(), any(), any(), eq(List.of(1L, 2L)), any());
        verify(leadRepository).findAllWithTagsByIdIn(List.of(10L), USUARIO_ID);
    }

    @Test
    @DisplayName("listarPaginado: la consulta de etiquetas de la página pasa estrictamente el usuarioId del propietario autenticado")
    void listarPaginado_tagFetchIsStrictlyScopedToOwner() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Lead l1 = new Lead();
        l1.setId(55L);
        l1.setUsuario(usuario);
        l1.setNombre("Lead Propietario");
        l1.setEtapa("NUEVO");

        Page<Lead> pageResult = new PageImpl<>(List.of(l1), PageRequest.of(0, 20), 1);
        when(leadRepository.buscar(eq(USUARIO_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageResult);
        when(leadRepository.findAllWithTagsByIdIn(List.of(55L), USUARIO_ID))
                .thenReturn(List.of(l1));

        PageResponse<LeadResponse> response = leadService.listarPaginado(
                USUARIO_ID, null, "all", null, 0);

        assertThat(response.content()).hasSize(1);
        verify(leadRepository).findAllWithTagsByIdIn(List.of(55L), USUARIO_ID);
    }
}
