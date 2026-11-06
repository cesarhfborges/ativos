package br.com.chfb.ativo.coreapi.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record AdminResetPasswordRequest(
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {

    /** O BCrypt só considera 72 bytes; um caractere acentuado ocupa mais de um. */
    @Schema(hidden = true)
    @AssertTrue(message = "A senha deve ter no máximo 72 bytes")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
