package com.urleads.database;

import com.urleads.entity.Notification;
import com.urleads.entity.Usuario;
import com.urleads.repository.NotificationRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
@Tag("db-integration")
@ActiveProfiles("db-integration")
class NotificationRepositoryIT {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Aislamiento estricto: consultas de un usuario jamás retornan notificaciones de otro")
    void userIsolation_QueriesOnlyOwnNotifications() {
        Usuario user1 = new Usuario();
        user1.setUsername("notif_user1");
        user1.setPasswordHash("hash1");
        user1 = usuarioRepository.save(user1);

        Usuario user2 = new Usuario();
        user2.setUsername("notif_user2");
        user2.setPasswordHash("hash2");
        user2 = usuarioRepository.save(user2);

        Notification n1 = Notification.builder()
                .usuario(user1)
                .tipo("ALERTA")
                .titulo("Notif User 1")
                .mensaje("Mensaje 1")
                .leida(false)
                .creadoEn(Instant.now().minusSeconds(10))
                .build();
        notificationRepository.save(n1);

        Notification n2 = Notification.builder()
                .usuario(user2)
                .tipo("ALERTA")
                .titulo("Notif User 2")
                .mensaje("Mensaje 2")
                .leida(false)
                .creadoEn(Instant.now())
                .build();
        notificationRepository.save(n2);

        // Consultar para user1
        Page<Notification> pageUser1 = notificationRepository.findByUsuarioIdOrderByCreadoEnDescIdDesc(
                user1.getId(), PageRequest.of(0, 10));

        assertThat(pageUser1.getContent()).hasSize(1);
        assertThat(pageUser1.getContent().get(0).getTitulo()).isEqualTo("Notif User 1");

        // user1 no puede obtener por ID la notificación de user2
        Optional<Notification> crossUserAttempt = notificationRepository.findByIdAndUsuarioId(n2.getId(), user1.getId());
        assertThat(crossUserAttempt).isEmpty();

        // user2 sí puede obtenerla
        Optional<Notification> validAttempt = notificationRepository.findByIdAndUsuarioId(n2.getId(), user2.getId());
        assertThat(validAttempt).isPresent();
    }

    @Test
    @DisplayName("Filtrado por leídas/no leídas y orden determinista (creadoEn DESC, id DESC)")
    void filterByRead_AndDeterministicOrdering() {
        Usuario user = new Usuario();
        user.setUsername("notif_filter_user");
        user.setPasswordHash("hash1");
        user = usuarioRepository.save(user);

        Instant base = Instant.now().minusSeconds(100);

        Notification unreadOld = notificationRepository.save(Notification.builder()
                .usuario(user).tipo("T1").titulo("No leída antigua").mensaje("m").leida(false)
                .creadoEn(base).build());

        Notification unreadNew = notificationRepository.save(Notification.builder()
                .usuario(user).tipo("T2").titulo("No leída nueva").mensaje("m").leida(false)
                .creadoEn(base.plusSeconds(50)).build());

        Notification readNotif = notificationRepository.save(Notification.builder()
                .usuario(user).tipo("T3").titulo("Leída").mensaje("m").leida(true)
                .leidaEn(Instant.now()).creadoEn(base.plusSeconds(25)).build());

        // Filtrar solo no leídas
        Page<Notification> unreadPage = notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(
                user.getId(), false, PageRequest.of(0, 10));
        assertThat(unreadPage.getContent()).hasSize(2);
        // Orden más reciente primero: unreadNew antes de unreadOld
        assertThat(unreadPage.getContent().get(0).getId()).isEqualTo(unreadNew.getId());
        assertThat(unreadPage.getContent().get(1).getId()).isEqualTo(unreadOld.getId());

        // Filtrar solo leídas
        Page<Notification> readPage = notificationRepository.findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(
                user.getId(), true, PageRequest.of(0, 10));
        assertThat(readPage.getContent()).hasSize(1);
        assertThat(readPage.getContent().get(0).getId()).isEqualTo(readNotif.getId());
    }
}
