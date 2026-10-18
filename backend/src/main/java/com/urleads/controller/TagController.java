package com.urleads.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.urleads.dto.TagRequest;
import com.urleads.dto.TagResponse;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.TagService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<TagResponse> listar() {
        return tagService.listar(currentUserProvider.getCurrentUserId());
    }

    @GetMapping("/{id}")
    public TagResponse obtener(@PathVariable Long id) {
        return tagService.obtener(id, currentUserProvider.getCurrentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse crear(@Valid @RequestBody TagRequest request) {
        return tagService.crear(currentUserProvider.getCurrentUserId(), request);
    }

    @PutMapping("/{id}")
    public TagResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request) {
        return tagService.actualizar(id, currentUserProvider.getCurrentUserId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        tagService.eliminar(id, currentUserProvider.getCurrentUserId());
    }
}
