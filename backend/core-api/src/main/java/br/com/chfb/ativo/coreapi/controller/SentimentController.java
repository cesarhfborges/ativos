package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.predictiveengine.dto.SentimentResponse;
import br.com.chfb.ativo.predictiveengine.service.PortfolioSentimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Com {@code predictive.enabled=false}, este endpoint deixa de existir e o card some do Painel. */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = OpenApiConfig.DASHBOARD_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class SentimentController {

    private final PortfolioSentimentService sentimentService;

    public SentimentController(PortfolioSentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    @Operation(
            summary = "Retorna o índice de ganância e medo da carteira, com os pesos atuais",
            description = "Descreve o comportamento dos preços dos ativos em carteira. Não é previsão nem recomendação de investimento.")
    @ApiResponse(responseCode = "200", description = "Leitura atual e pontos diários do período")
    @ApiResponse(responseCode = "400", description = "Período inválido", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/sentiment")
    public SentimentResponse sentiment(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return sentimentService.sentiment(user.getId(), days);
    }
}
