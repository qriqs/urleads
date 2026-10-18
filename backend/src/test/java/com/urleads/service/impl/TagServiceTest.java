package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.urleads.dto.TagRequest;
import com.urleads.dto.TagResponse;
import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.exception.TagDuplicateException;
import com.urleads.exception.TagNotFoundException;
import com.urleads.repository.TagRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.TagService;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private TagService tagService;

    private static final Long USUARIO_ID = 10L;
    private static final Long OTRO_USUARIO_ID = 20L;

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagRepository, usuarioRepository);
    }

    @Test
    @DisplayName("listar: retorna únicamente las etiquetas del propietario")
    void listar_retornaEtiquetasDelUsuario() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Tag t1 = new Tag(usuario, "Prospecto");
        t1.setId(1L);
        t1.setCreadoEn(OffsetDateTime.now());

        Tag t2 = new Tag(usuario, "VIP");
        t2.setId(2L);
        t2.setCreadoEn(OffsetDateTime.now());

        when(tagRepository.findByUsuarioIdOrderByNombreAsc(USUARIO_ID)).thenReturn(List.of(t1, t2));

        List<TagResponse> resultado = tagService.listar(USUARIO_ID);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).name()).isEqualTo("Prospecto");
        assertThat(resultado.get(1).name()).isEqualTo("VIP");
        verify(tagRepository).findByUsuarioIdOrderByNombreAsc(USUARIO_ID);
    }

    @Test
    @DisplayName("crear: recorta espacios y guarda la etiqueta con el propietario del servidor")
    void crear_recortaEspaciosYPersiste() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        when(tagRepository.existsByUsuarioIdAndNombreIgnoreCase(USUARIO_ID, "Prospecto")).thenReturn(false);
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));

        Tag guardado = new Tag(usuario, "Prospecto");
        guardado.setId(100L);
        guardado.setCreadoEn(OffsetDateTime.now());
        when(tagRepository.save(any(Tag.class))).thenReturn(guardado);

        TagResponse response = tagService.crear(USUARIO_ID, new TagRequest("   Prospecto   "));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("Prospecto");
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    @DisplayName("crear: rechaza nombre en blanco")
    void crear_rechazaNombreEnBlanco() {
        assertThatThrownBy(() -> tagService.crear(USUARIO_ID, new TagRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede estar vacío");

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza nombre duplicado case-insensitive para el mismo propietario")
    void crear_rechazaDuplicadoMismoPropietario() {
        when(tagRepository.existsByUsuarioIdAndNombreIgnoreCase(USUARIO_ID, "PROSPECTO")).thenReturn(true);

        assertThatThrownBy(() -> tagService.crear(USUARIO_ID, new TagRequest("  PROSPECTO  ")))
                .isInstanceOf(TagDuplicateException.class)
                .hasMessageContaining("Ya existe una etiqueta con el nombre");

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("obtener: lanza TagNotFoundException cuando la etiqueta no pertenece al usuario")
    void obtener_etiquetaAjenaOLanzada_devuelve404() {
        when(tagRepository.findByIdAndUsuarioId(999L, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.obtener(999L, USUARIO_ID))
                .isInstanceOf(TagNotFoundException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    @DisplayName("actualizar: renombra etiqueta recortando espacios")
    void actualizar_renombraEtiqueta() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Tag existente = new Tag(usuario, "Antiguo");
        existente.setId(5L);

        when(tagRepository.findByIdAndUsuarioId(5L, USUARIO_ID)).thenReturn(Optional.of(existente));
        when(tagRepository.existsByUsuarioIdAndNombreIgnoreCaseAndIdNot(USUARIO_ID, "Nuevo", 5L)).thenReturn(false);

        Tag actualizado = new Tag(usuario, "Nuevo");
        actualizado.setId(5L);
        actualizado.setCreadoEn(OffsetDateTime.now());
        when(tagRepository.save(existente)).thenReturn(actualizado);

        TagResponse response = tagService.actualizar(5L, USUARIO_ID, new TagRequest("  Nuevo  "));

        assertThat(response.name()).isEqualTo("Nuevo");
        verify(tagRepository).save(existente);
    }

    @Test
    @DisplayName("actualizar: rechaza duplicado case-insensitive con otra etiqueta del mismo propietario")
    void actualizar_rechazaDuplicadoConOtraEtiqueta() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Tag existente = new Tag(usuario, "Tag A");
        existente.setId(5L);

        when(tagRepository.findByIdAndUsuarioId(5L, USUARIO_ID)).thenReturn(Optional.of(existente));
        when(tagRepository.existsByUsuarioIdAndNombreIgnoreCaseAndIdNot(USUARIO_ID, "Tag B", 5L)).thenReturn(true);

        assertThatThrownBy(() -> tagService.actualizar(5L, USUARIO_ID, new TagRequest("Tag B")))
                .isInstanceOf(TagDuplicateException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("eliminar: limpia asociaciones de lead_tag y elimina la etiqueta")
    void eliminar_limpiaAsociacionesYEliminaEtiqueta() {
        Usuario usuario = new Usuario(USUARIO_ID, "propietario", "hash");
        Tag existente = new Tag(usuario, "Para Borrar");
        existente.setId(7L);

        when(tagRepository.findByIdAndUsuarioId(7L, USUARIO_ID)).thenReturn(Optional.of(existente));

        tagService.eliminar(7L, USUARIO_ID);

        verify(tagRepository).deleteLeadTagAssociationsByTagId(7L);
        verify(tagRepository).delete(existente);
    }

    @Test
    @DisplayName("eliminar: lanza TagNotFoundException si la etiqueta pertenece a otro usuario")
    void eliminar_etiquetaAjena_lanzaTagNotFoundException() {
        when(tagRepository.findByIdAndUsuarioId(7L, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.eliminar(7L, USUARIO_ID))
                .isInstanceOf(TagNotFoundException.class);

        verify(tagRepository, never()).delete(any());
        verify(tagRepository, never()).deleteLeadTagAssociationsByTagId(any());
    }
}
