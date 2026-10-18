package com.urleads.repository;

import com.urleads.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId, Pageable pageable);

    Page<Notification> findByUsuarioIdAndLeidaOrderByCreadoEnDescIdDesc(Long usuarioId, boolean leida, Pageable pageable);

    Optional<Notification> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<Notification> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    @Modifying
    @Query("UPDATE Notification n SET n.leida = true, n.leidaEn = :leidaEn WHERE n.usuario.id = :usuarioId AND n.leida = false")
    int markAllAsRead(@Param("usuarioId") Long usuarioId, @Param("leidaEn") Instant leidaEn);
}
