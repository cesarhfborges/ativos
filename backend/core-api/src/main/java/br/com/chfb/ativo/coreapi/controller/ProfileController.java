package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.ChangePasswordRequest;
import br.com.chfb.ativo.coreapi.dto.ProfileResponse;
import br.com.chfb.ativo.coreapi.dto.UpdateProfileRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = OpenApiConfig.PROFILE_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Retorna o perfil do usuário logado")
    @ApiResponse(responseCode = "200", description = "Perfil")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping
    public ProfileResponse get(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.get(user.getId());
    }

    @Operation(summary = "Atualiza o perfil", description = "O e-mail não muda: é a identidade de login.")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @PutMapping
    public ProfileResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest body) {
        return profileService.update(user.getId(), body);
    }

    @Operation(summary = "Troca a senha", description = "Exige a senha atual e encerra as outras sessões do usuário.")
    @ApiResponse(responseCode = "204", description = "Senha alterada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Senha atual incorreta",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest body,
            @Parameter(hidden = true) HttpSession session) {
        profileService.changePassword(user.getId(), body, session.getId());
    }
}
