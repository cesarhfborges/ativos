package br.com.chfb.ativo.coreapi.controller.admin;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.admin.AdminCreateUserRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminResetPasswordRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminSetActiveRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminUpdateUserRequest;
import br.com.chfb.ativo.coreapi.dto.admin.AdminUserResponse;
import br.com.chfb.ativo.coreapi.dto.admin.PageResponse;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = OpenApiConfig.ADMIN_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @Operation(summary = "Lista as contas, com busca por nome ou e-mail")
    @GetMapping
    public PageResponse<AdminUserResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(query, page, size);
    }

    @Operation(summary = "Cria uma conta", description = "Funciona mesmo com o cadastro público fechado.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(
            @Valid @RequestBody AdminCreateUserRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(body, actor);
    }

    @Operation(summary = "Edita uma conta", description = "Mudar e-mail ou papel encerra as sessões da conta.")
    @PutMapping("/{id}")
    public AdminUserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, body, actor);
    }

    @Operation(summary = "Ativa ou desativa uma conta", description = "Desativar encerra as sessões e impede novos logins; nada é apagado.")
    @PutMapping("/{id}/active")
    public AdminUserResponse setActive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminSetActiveRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.setActive(id, body.active(), actor);
    }

    @Operation(summary = "Define uma nova senha", description = "Encerra as sessões da conta.")
    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        service.resetPassword(id, body.password(), actor);
    }

    @Operation(summary = "Encerra as sessões da conta")
    @PostMapping("/{id}/sessions/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endSessions(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        service.endSessions(id, actor);
    }
}
