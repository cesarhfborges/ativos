package br.com.chfb.ativo.coreapi.dto.admin;

import java.util.List;

public record PageResponse<T>(List<T> items, long total) {
}
