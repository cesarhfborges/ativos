package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.SimulationRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.predictiveengine.dto.SimulationResponse;
import br.com.chfb.ativo.predictiveengine.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Com {@code predictive.enabled=false}, este endpoint deixa de existir. */
@RestController
@RequestMapping("/api/predictions")
@Tag(name = OpenApiConfig.PREDICTION_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Operation(
            summary = "Simula a evolução do patrimônio em um cenário",
            description = "Devolve a curva projetada com intervalo de confiança, as premissas e os alertas de risco. "
                    + "É uma simulação, não uma previsão nem recomendação de investimento.")
    @ApiResponse(responseCode = "200", description = "Simulação calculada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @PostMapping("/simulate")
    public SimulationResponse simulate(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SimulationRequest body) {
        return predictionService.simulate(user.getId(), body.months(), body.contributionOrZero(), body.scenario());
    }
}
