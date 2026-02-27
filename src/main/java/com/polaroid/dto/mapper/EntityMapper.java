package com.polaroid.dto.mapper;

public interface EntityMapper<D, E> {
    E toDto(D entity);
    D toEntity(E dto);
}
