package com.urleads.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;

/**
 * Pruebas de integración de persistencia JPA para Leads (UL-07).
 * Valida operaciones CRUD y aislamiento estricto por propietario (Owner Isolation).
 */
@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
class LeadRepositoryIT {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Insert: guarda un lead con fechas autogeneradas y recupera por ID")
    void shouldInsertLead() {
        Usuario usuario = new Usuario();
        usuario.setUsername("user_insert");
        usuario.setPasswordHash("hash123");
        usuario = usuarioRepository.save(usuario);

        Lead lead = new Lead();
        lead.setUsuario(usuario);
        lead.setNombre("Juan Perez");
        lead.setTelefono("+51999999999");
        lead.setCorreo("juan@test.com");
        lead.setEtapa("NUEVO");

        Lead saved = leadRepository.save(lead);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreadoEn()).isNotNull();
        assertThat(saved.getActualizadoEn()).isNotNull();

        Lead reloaded = leadRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getNombre()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("List: lista únicamente los leads que pertenecen al usuario consultado")
    void shouldListLeadsByOwner() {
        Usuario usuarioA = new Usuario();
        usuarioA.setUsername("ownerA");
        usuarioA.setPasswordHash("hash");
        usuarioA = usuarioRepository.save(usuarioA);

        Usuario usuarioB = new Usuario();
        usuarioB.setUsername("ownerB");
        usuarioB.setPasswordHash("hash");
        usuarioB = usuarioRepository.save(usuarioB);

        Lead leadA = new Lead();
        leadA.setUsuario(usuarioA);
        leadA.setNombre("Lead A");
        leadA.setTelefono("999");
        leadA.setEtapa("NUEVO");
        leadRepository.save(leadA);

        Lead leadB = new Lead();
        leadB.setUsuario(usuarioB);
        leadB.setNombre("Lead B");
        leadB.setTelefono("888");
        leadB.setEtapa("NUEVO");
        leadRepository.save(leadB);

        List<Lead> leadsA = leadRepository.findByUsuarioId(usuarioA.getId());
        List<Lead> leadsB = leadRepository.findByUsuarioId(usuarioB.getId());

        assertThat(leadsA).hasSize(1);
        assertThat(leadsA.get(0).getNombre()).isEqualTo("Lead A");

        assertThat(leadsB).hasSize(1);
        assertThat(leadsB.get(0).getNombre()).isEqualTo("Lead B");
    }

    @Test
    @DisplayName("Owner Isolation: un usuario no puede consultar por ID el lead de otro usuario")
    void shouldEnforceOwnerIsolationOnFindById() {
        Usuario usuarioA = new Usuario();
        usuarioA.setUsername("user_iso_a");
        usuarioA.setPasswordHash("hash");
        usuarioA = usuarioRepository.save(usuarioA);

        Usuario usuarioB = new Usuario();
        usuarioB.setUsername("user_iso_b");
        usuarioB.setPasswordHash("hash");
        usuarioB = usuarioRepository.save(usuarioB);

        Lead leadA = new Lead();
        leadA.setUsuario(usuarioA);
        leadA.setNombre("Lead Privado A");
        leadA.setTelefono("999");
        leadA.setEtapa("NUEVO");
        leadA = leadRepository.save(leadA);

        // Usuario B intenta acceder al lead de Usuario A
        Optional<Lead> intentoAcceso = leadRepository.findByIdAndUsuarioId(leadA.getId(), usuarioB.getId());

        assertThat(intentoAcceso).as("Usuario B no debe tener acceso al lead de Usuario A").isEmpty();

        // Usuario A accede a su propio lead
        Optional<Lead> accesoValido = leadRepository.findByIdAndUsuarioId(leadA.getId(), usuarioA.getId());
        assertThat(accesoValido).isPresent();
    }

    @Test
    @DisplayName("Update: actualiza atributos del lead y persiste el cambio")
    void shouldUpdateLead() {
        Usuario usuario = new Usuario();
        usuario.setUsername("updateUser");
        usuario.setPasswordHash("hash");
        usuario = usuarioRepository.save(usuario);

        Lead lead = new Lead();
        lead.setUsuario(usuario);
        lead.setNombre("Nombre Original");
        lead.setTelefono("999");
        lead.setEtapa("NUEVO");
        lead = leadRepository.save(lead);

        lead.setNombre("Nombre Actualizado");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.now().plusDays(5));
        leadRepository.save(lead);

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getNombre()).isEqualTo("Nombre Actualizado");
        assertThat(reloaded.getEtapa()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(reloaded.getProximoSeguimiento()).isEqualTo(LocalDate.now().plusDays(5));
    }

    @Test
    @DisplayName("Delete: elimina un lead y confirma que no existe")
    void shouldDeleteLead() {
        Usuario usuario = new Usuario();
        usuario.setUsername("deleteUser");
        usuario.setPasswordHash("hash");
        usuario = usuarioRepository.save(usuario);

        Lead lead = new Lead();
        lead.setUsuario(usuario);
        lead.setNombre("Lead a Eliminar");
        lead.setTelefono("999");
        lead.setEtapa("NUEVO");
        Lead saved = leadRepository.save(lead);

        leadRepository.deleteById(saved.getId());

        Optional<Lead> deleted = leadRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }
}