package com.gymvip.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * DTO canónico y estable para la serialización de respuestas paginadas.
 * Desacopla la API pública de la clase interna org.springframework.data.domain.PageImpl,
 * preservando exactamente la estructura JSON y semántica consumida por frontend y clientes móviles:
 * content, totalElements, totalPages, size, number, first, last, empty, numberOfElements.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty,
        int numberOfElements
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        if (page == null) {
            return new PageResponse<>(List.of(), 0, 0, 0L, 0, true, true, true, 0);
        }
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty(),
                page.getNumberOfElements()
        );
    }

    /**
     * Alias opcional 'page' para clientes que inspeccionen el número de página.
     */
    @JsonProperty("page")
    public int page() {
        return number;
    }
}
