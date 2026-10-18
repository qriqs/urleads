package com.urleads.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urleads.dto.TagRequest;
import com.urleads.dto.TagResponse;
import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.exception.TagDuplicateException;
import com.urleads.exception.TagNotFoundException;
import com.urleads.exception.UsuarioNotFoundException;
import com.urleads.repository.TagRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.TagService;

@Service
@Transactional
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired
    public TagServiceImpl(@Autowired(required = false) TagRepository tagRepository,
                          @Autowired(required = false) UsuarioRepository usuarioRepository) {
        this.tagRepository = tagRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> listar(Long usuarioId) {
        if (tagRepository == null) {
            return List.of();
        }
        return tagRepository.findByUsuarioIdOrderByNombreAsc(usuarioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse obtener(Long tagId, Long usuarioId) {
        if (tagRepository == null) {
            throw new TagNotFoundException("Etiqueta no encontrada");
        }
        Tag tag = tagRepository.findByIdAndUsuarioId(tagId, usuarioId)
                .orElseThrow(() -> new TagNotFoundException("Etiqueta no encontrada"));
        return toResponse(tag);
    }

    @Override
    public TagResponse crear(Long usuarioId, TagRequest request) {
        if (request == null || request.name() == null || request.name().trim().isBlank()) {
            throw new IllegalArgumentException("El nombre de la etiqueta no puede estar vacío");
        }
        String trimmedName = request.name().trim();

        if (tagRepository != null && tagRepository.existsByUsuarioIdAndNombreIgnoreCase(usuarioId, trimmedName)) {
            throw new TagDuplicateException("Ya existe una etiqueta con el nombre: " + trimmedName);
        }

        if (usuarioRepository == null || tagRepository == null) {
            throw new IllegalStateException("Repositorios no configurados");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

        Tag tag = new Tag(usuario, trimmedName);
        try {
            Tag saved = tagRepository.save(tag);
            tagRepository.flush();
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new TagDuplicateException("Ya existe una etiqueta con el nombre: " + trimmedName);
        }
    }

    @Override
    public TagResponse actualizar(Long tagId, Long usuarioId, TagRequest request) {
        if (request == null || request.name() == null || request.name().trim().isBlank()) {
            throw new IllegalArgumentException("El nombre de la etiqueta no puede estar vacío");
        }
        String trimmedName = request.name().trim();

        if (tagRepository == null) {
            throw new TagNotFoundException("Etiqueta no encontrada");
        }

        Tag tag = tagRepository.findByIdAndUsuarioId(tagId, usuarioId)
                .orElseThrow(() -> new TagNotFoundException("Etiqueta no encontrada"));

        if (tagRepository.existsByUsuarioIdAndNombreIgnoreCaseAndIdNot(usuarioId, trimmedName, tagId)) {
            throw new TagDuplicateException("Ya existe una etiqueta con el nombre: " + trimmedName);
        }

        tag.setNombre(trimmedName);
        try {
            Tag saved = tagRepository.save(tag);
            tagRepository.flush();
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new TagDuplicateException("Ya existe una etiqueta con el nombre: " + trimmedName);
        }
    }

    @Override
    public void eliminar(Long tagId, Long usuarioId) {
        if (tagRepository == null) {
            throw new TagNotFoundException("Etiqueta no encontrada");
        }

        Tag tag = tagRepository.findByIdAndUsuarioId(tagId, usuarioId)
                .orElseThrow(() -> new TagNotFoundException("Etiqueta no encontrada"));

        tagRepository.deleteLeadTagAssociationsByTagId(tagId);
        tagRepository.delete(tag);
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getNombre(), tag.getCreadoEn());
    }
}
