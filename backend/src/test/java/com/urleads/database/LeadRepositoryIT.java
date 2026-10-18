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

    @Test
    @DisplayName("Dashboard: countLeadsPorEtapa agrupa por etapas canónicas y respeta aislamiento de propietario")
    void shouldCountLeadsPorEtapaCanonical() {
        Usuario usuarioA = new Usuario();
        usuarioA.setUsername("dashUserA");
        usuarioA.setPasswordHash("hash");
        usuarioA = usuarioRepository.save(usuarioA);

        Usuario usuarioB = new Usuario();
        usuarioB.setUsername("dashUserB");
        usuarioB.setPasswordHash("hash");
        usuarioB = usuarioRepository.save(usuarioB);

        // Usuario A: 2 NUEVO, 1 EN_SEGUIMIENTO, 1 CERRADO
        Lead l1 = new Lead();
        l1.setUsuario(usuarioA);
        l1.setNombre("Lead N1");
        l1.setTelefono("111");
        l1.setEtapa("NUEVO");
        leadRepository.save(l1);

        Lead l2 = new Lead();
        l2.setUsuario(usuarioA);
        l2.setNombre("Lead N2");
        l2.setTelefono("222");
        l2.setEtapa("NUEVO");
        leadRepository.save(l2);

        Lead l3 = new Lead();
        l3.setUsuario(usuarioA);
        l3.setNombre("Lead S1");
        l3.setTelefono("333");
        l3.setEtapa("EN_SEGUIMIENTO");
        leadRepository.save(l3);

        Lead l4 = new Lead();
        l4.setUsuario(usuarioA);
        l4.setNombre("Lead C1");
        l4.setTelefono("444");
        l4.setEtapa("CERRADO");
        leadRepository.save(l4);

        // Usuario B: 1 NUEVO (no debe contarse para usuario A)
        Lead lB = new Lead();
        lB.setUsuario(usuarioB);
        lB.setNombre("Lead B");
        lB.setTelefono("555");
        lB.setEtapa("NUEVO");
        leadRepository.save(lB);

        List<Object[]> agrupados = leadRepository.countLeadsPorEtapa(usuarioA.getId());
        java.util.Map<String, Long> conteos = new java.util.HashMap<>();
        for (Object[] fila : agrupados) {
            conteos.put((String) fila[0], (Long) fila[1]);
        }

        assertThat(conteos)
                .containsEntry("NUEVO", 2L)
                .containsEntry("EN_SEGUIMIENTO", 1L)
                .containsEntry("CERRADO", 1L)
                .doesNotContainKey("GANADO")
                .doesNotContainKey("PERDIDO");
    }

    @Test
    @DisplayName("Dashboard: findSeguimientosHoy excluye CERRADO y respeta orden por nombre y aislamiento")
    void shouldFindSeguimientosHoyExcludingCerrado() {
        LocalDate hoy = LocalDate.now();

        Usuario usuarioA = new Usuario();
        usuarioA.setUsername("hoyUserA");
        usuarioA.setPasswordHash("hash");
        usuarioA = usuarioRepository.save(usuarioA);

        Usuario usuarioB = new Usuario();
        usuarioB.setUsername("hoyUserB");
        usuarioB.setPasswordHash("hash");
        usuarioB = usuarioRepository.save(usuarioB);

        Lead l1 = new Lead();
        l1.setUsuario(usuarioA);
        l1.setNombre("Zeta Lead Hoy");
        l1.setTelefono("111");
        l1.setEtapa("EN_SEGUIMIENTO");
        l1.setProximoSeguimiento(hoy);
        leadRepository.save(l1);

        Lead l2 = new Lead();
        l2.setUsuario(usuarioA);
        l2.setNombre("Alfa Lead Hoy");
        l2.setTelefono("222");
        l2.setEtapa("NUEVO");
        l2.setProximoSeguimiento(hoy);
        leadRepository.save(l2);

        // Lead cerrado (sin seguimiento por restriccion de BD)
        Lead lCerrado = new Lead();
        lCerrado.setUsuario(usuarioA);
        lCerrado.setNombre("Cerrado");
        lCerrado.setTelefono("333");
        lCerrado.setEtapa("CERRADO");
        lCerrado.setProximoSeguimiento(null);
        leadRepository.save(lCerrado);

        // Lead de hoy de otro usuario
        Lead lOtro = new Lead();
        lOtro.setUsuario(usuarioB);
        lOtro.setNombre("Otro Hoy");
        lOtro.setTelefono("444");
        lOtro.setEtapa("EN_SEGUIMIENTO");
        lOtro.setProximoSeguimiento(hoy);
        leadRepository.save(lOtro);

        List<Lead> hoyLeads = leadRepository.findSeguimientosHoy(usuarioA.getId(), hoy);
        assertThat(hoyLeads).hasSize(2);
        assertThat(hoyLeads.get(0).getNombre()).isEqualTo("Alfa Lead Hoy");
        assertThat(hoyLeads.get(1).getNombre()).isEqualTo("Zeta Lead Hoy");
    }

    @Test
    @DisplayName("Dashboard: findSeguimientosAtrasados excluye CERRADO y ordena por fecha ASC y nombre ASC")
    void shouldFindSeguimientosAtrasadosExcludingCerrado() {
        LocalDate hoy = LocalDate.now();

        Usuario usuarioA = new Usuario();
        usuarioA.setUsername("atrasadoUserA");
        usuarioA.setPasswordHash("hash");
        usuarioA = usuarioRepository.save(usuarioA);

        Usuario usuarioB = new Usuario();
        usuarioB.setUsername("atrasadoUserB");
        usuarioB.setPasswordHash("hash");
        usuarioB = usuarioRepository.save(usuarioB);

        Lead l1 = new Lead();
        l1.setUsuario(usuarioA);
        l1.setNombre("Beta Mas Antiguo");
        l1.setTelefono("111");
        l1.setEtapa("EN_SEGUIMIENTO");
        l1.setProximoSeguimiento(hoy.minusDays(5));
        leadRepository.save(l1);

        Lead l2 = new Lead();
        l2.setUsuario(usuarioA);
        l2.setNombre("Alpha Mas Antiguo");
        l2.setTelefono("222");
        l2.setEtapa("NUEVO");
        l2.setProximoSeguimiento(hoy.minusDays(5));
        leadRepository.save(l2);

        Lead l3 = new Lead();
        l3.setUsuario(usuarioA);
        l3.setNombre("Ayer");
        l3.setTelefono("333");
        l3.setEtapa("NUEVO");
        l3.setProximoSeguimiento(hoy.minusDays(1));
        leadRepository.save(l3);

        // Lead de hoy (no debe estar en atrasados)
        Lead lHoy = new Lead();
        lHoy.setUsuario(usuarioA);
        lHoy.setNombre("Hoy");
        lHoy.setTelefono("444");
        lHoy.setEtapa("NUEVO");
        lHoy.setProximoSeguimiento(hoy);
        leadRepository.save(lHoy);

        // Lead atrasado de otro usuario
        Lead lOtro = new Lead();
        lOtro.setUsuario(usuarioB);
        lOtro.setNombre("Otro Atrasado");
        lOtro.setTelefono("555");
        lOtro.setEtapa("NUEVO");
        lOtro.setProximoSeguimiento(hoy.minusDays(3));
        leadRepository.save(lOtro);

        List<Lead> atrasados = leadRepository.findSeguimientosAtrasados(usuarioA.getId(), hoy);
        assertThat(atrasados).hasSize(3);
        assertThat(atrasados.get(0).getNombre()).isEqualTo("Alpha Mas Antiguo");
        assertThat(atrasados.get(1).getNombre()).isEqualTo("Beta Mas Antiguo");
        assertThat(atrasados.get(2).getNombre()).isEqualTo("Ayer");
    }
}