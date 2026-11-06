package br.com.chfb.ativo.coreapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Página no formato do {@code simplePaginate} do Laravel: sem total nem última página, porque não há contagem.
 * Os links são relativos, para valerem atrás de proxy, e mantêm os filtros da requisição.
 */
public record SimplePage<T>(
        @JsonProperty("current_page") int currentPage,
        List<T> data,
        @JsonProperty("first_page_url") String firstPageUrl,
        Integer from,
        @JsonProperty("next_page_url") String nextPageUrl,
        String path,
        @JsonProperty("per_page") int perPage,
        @JsonProperty("prev_page_url") String prevPageUrl,
        Integer to) {

    public static <T> SimplePage<T> of(List<T> items, boolean hasMore, int page, int perPage, HttpServletRequest request) {
        int offset = (page - 1) * perPage;
        return new SimplePage<>(
                page,
                items,
                url(request, 1),
                items.isEmpty() ? null : offset + 1,
                hasMore ? url(request, page + 1) : null,
                request.getRequestURI(),
                perPage,
                page > 1 ? url(request, page - 1) : null,
                items.isEmpty() ? null : offset + items.size());
    }

    private static String url(HttpServletRequest request, int page) {
        return UriComponentsBuilder.fromPath(request.getRequestURI())
                .query(request.getQueryString())
                .replaceQueryParam("page", page)
                .build()
                .toUriString();
    }
}
