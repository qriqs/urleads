package com.urleads.repository;

import com.urleads.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    Optional<UserPreferences> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
