package com.urleads.repository;

import com.urleads.entity.AgendaCitaMinuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgendaCitaMinutaRepository extends JpaRepository<AgendaCitaMinuta, Long> {

    Optional<AgendaCitaMinuta> findByCitaIdAndUsuarioId(Long citaId, Long usuarioId);

    Optional<AgendaCitaMinuta> findByCitaId(Long citaId);

    boolean existsByCitaId(Long citaId);

    void deleteByCitaIdAndUsuarioId(Long citaId, Long usuarioId);
}
