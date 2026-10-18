package com.urleads.comercial.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        List<D> converted = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
            converted,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
        );
    }
}
