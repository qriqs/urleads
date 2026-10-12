package com.urleads.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urleads.entity.Usuario;

public interface UsuarioRepository
        extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);
}