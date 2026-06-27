package com.example.bankcards.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(name = "PageResponse", description = "Постраничный результат выборки")
public record PageResponse<T>(
        @Schema(description = "Элементы текущей страницы")
        List<T> content,
        @Schema(description = "Номер текущей страницы (с нуля)", example = "0")
        int page,
        @Schema(description = "Размер страницы", example = "20")
        int size,
        @Schema(description = "Общее количество элементов", example = "42")
        long totalElements,
        @Schema(description = "Общее количество страниц", example = "3")
        int totalPages,
        @Schema(description = "Является ли текущая страница последней", example = "false")
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
