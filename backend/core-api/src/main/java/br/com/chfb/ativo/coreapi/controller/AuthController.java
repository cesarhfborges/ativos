package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.LoginRequest;
import br.com.chfb.ativo.coreapi.dto.RegisterRequest;
import br.com.chfb.ativo.coreapi.dto.UserResponse;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** O logout ({@code POST /api/auth/logout}) é tratado pelo filtro definido em {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = OpenApiConfig.AUTH_TAG)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Entrega o token CSRF",
            description = "Grava o cookie XSRF-TOKEN. Chame antes do primeiro POST e depois do logout.")
    @ApiResponse(responseCode = "204", description = "Cookie XSRF-TOKEN gravado")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(@Parameter(hidden = true) CsrfToken token) {
        // Ler o token é o que faz o filtro de CSRF gravar o cookie na resposta.
        token.getToken();
    }

    @Operation(summary = "Cria uma conta", description = "Não inicia a sessão: depois do registro, faça o login.")
    @ApiResponse(responseCode = "201", description = "Conta criada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "E-mail já cadastrado",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest body) {
        return authService.register(body);
    }

    @Operation(summary = "Inicia a sessão", description = "Grava o cookie JSESSIONID e renova o token CSRF.")
    @ApiResponse(responseCode = "200", description = "Sessão iniciada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "401",
            description = "E-mail ou senha inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return authService.login(body, request, response);
    }

    @Operation(
            summary = "Retorna o usuário logado",
            security = @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME))
    @ApiResponse(responseCode = "200", description = "Usuário logado")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/me")
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.from(user);
    }
}
