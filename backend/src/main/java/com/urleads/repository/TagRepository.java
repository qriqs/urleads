package com.urleads.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.urleads.entity.Tag;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByUsuarioIdOrderByNombreAsc(Long usuarioId);

    Optional<Tag> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndNombreIgnoreCase(Long usuarioId, String nombre);

    boolean existsByUsuarioIdAndNombreIgnoreCaseAndIdNot(Long usuarioId, String nombre, Long id);

    List<Tag> findByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);

    @Modifying
    @Query(value = "DELETE FROM lead_tag WHERE tag_id = :tagId", nativeQuery = true)
    void deleteLeadTagAssociationsByTagId(@Param("tagId") Long tagId);
}
