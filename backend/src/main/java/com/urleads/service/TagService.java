package com.urleads.service;

import java.util.List;

import com.urleads.dto.TagRequest;
import com.urleads.dto.TagResponse;

public interface TagService {

    List<TagResponse> listar(Long usuarioId);

    TagResponse obtener(Long tagId, Long usuarioId);

    TagResponse crear(Long usuarioId, TagRequest request);

    TagResponse actualizar(Long tagId, Long usuarioId, TagRequest request);

    void eliminar(Long tagId, Long usuarioId);
}
