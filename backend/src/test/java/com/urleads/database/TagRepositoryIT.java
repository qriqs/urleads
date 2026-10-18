package com.urleads.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.repository.TagRepository;
import com.urleads.repository.UsuarioRepository;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
class TagRepositoryIT {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Insert: guarda etiqueta con fecha autogenerada y la recupera por ID")
    void shouldInsertAndFindTag() {
        Usuario usuario = new Usuario("user_tag_1", "hash123");
        usuario = usuarioRepository.save(usuario);

        Tag tag = new Tag(usuario, "Cliente VIP");
        Tag saved = tagRepository.save(tag);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreadoEn()).isNotNull();

        Optional<Tag> reloaded = tagRepository.findByIdAndUsuarioId(saved.getId(), usuario.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getNombre()).isEqualTo("Cliente VIP");
    }

    @Test
    @DisplayName("Owner Isolation: un usuario no puede consultar por ID la etiqueta de otro usuario")
    void shouldEnforceOwnerIsolationOnFindById() {
        Usuario usuarioA = usuarioRepository.save(new Usuario("user_iso_tag_a", "hash"));
        Usuario usuarioB = usuarioRepository.save(new Usuario("user_iso_tag_b", "hash"));

        Tag tagA = tagRepository.save(new Tag(usuarioA, "Etiqueta Privada A"));

        Optional<Tag> accesoB = tagRepository.findByIdAndUsuarioId(tagA.getId(), usuarioB.getId());
        assertThat(accesoB).isEmpty();

        Optional<Tag> accesoA = tagRepository.findByIdAndUsuarioId(tagA.getId(), usuarioA.getId());
        assertThat(accesoA).isPresent();
    }

    @Test
    @DisplayName("List: lista etiquetas ordenadas por nombre ASC únicamente del propietario consultado")
    void shouldListOrderedByNombreForOwnerOnly() {
        Usuario usuarioA = usuarioRepository.save(new Usuario("user_list_tag_a", "hash"));
        Usuario usuarioB = usuarioRepository.save(new Usuario("user_list_tag_b", "hash"));

        tagRepository.save(new Tag(usuarioA, "Zeta"));
        tagRepository.save(new Tag(usuarioA, "Alfa"));
        tagRepository.save(new Tag(usuarioA, "Beta"));
        tagRepository.save(new Tag(usuarioB, "Gamma"));

        List<Tag> tagsA = tagRepository.findByUsuarioIdOrderByNombreAsc(usuarioA.getId());
        assertThat(tagsA).hasSize(3);
        assertThat(tagsA.stream().map(Tag::getNombre).toList())
                .containsExactly("Alfa", "Beta", "Zeta");

        List<Tag> tagsB = tagRepository.findByUsuarioIdOrderByNombreAsc(usuarioB.getId());
        assertThat(tagsB).hasSize(1);
        assertThat(tagsB.get(0).getNombre()).isEqualTo("Gamma");
    }

    @Test
    @DisplayName("exists: verifica existencia case-insensitive por usuario")
    void shouldCheckExistenceCaseInsensitive() {
        Usuario usuario = usuarioRepository.save(new Usuario("user_exists_tag", "hash"));
        Tag saved = tagRepository.save(new Tag(usuario, "Prospecto"));

        assertThat(tagRepository.existsByUsuarioIdAndNombreIgnoreCase(usuario.getId(), "prospecto")).isTrue();
        assertThat(tagRepository.existsByUsuarioIdAndNombreIgnoreCase(usuario.getId(), "PROSPECTO")).isTrue();
        assertThat(tagRepository.existsByUsuarioIdAndNombreIgnoreCase(usuario.getId(), "Inexistente")).isFalse();

        assertThat(tagRepository.existsByUsuarioIdAndNombreIgnoreCaseAndIdNot(usuario.getId(), "prospecto", saved.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("findByUsuarioIdAndIdIn: recupera únicamente etiquetas pertenecientes al usuario")
    void shouldFindByIdInScopedToOwner() {
        Usuario usuarioA = usuarioRepository.save(new Usuario("user_in_a", "hash"));
        Usuario usuarioB = usuarioRepository.save(new Usuario("user_in_b", "hash"));

        Tag tA1 = tagRepository.save(new Tag(usuarioA, "A1"));
        Tag tA2 = tagRepository.save(new Tag(usuarioA, "A2"));
        Tag tB1 = tagRepository.save(new Tag(usuarioB, "B1"));

        List<Tag> encontradas = tagRepository.findByUsuarioIdAndIdIn(
                usuarioA.getId(),
                List.of(tA1.getId(), tA2.getId(), tB1.getId(), 999999L)
        );

        assertThat(encontradas).hasSize(2);
        assertThat(encontradas.stream().map(Tag::getNombre).toList())
                .containsExactlyInAnyOrder("A1", "A2");
    }
}
