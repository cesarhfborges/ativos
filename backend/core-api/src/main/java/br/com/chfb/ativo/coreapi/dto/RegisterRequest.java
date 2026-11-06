package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.coreapi.entity.InvestorProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public record RegisterRequest(
        @Schema(example = "Ana")
        @NotBlank @Size(max = 120)
        String name,

        @Schema(example = "ana@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "senha-forte-123")
        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @Schema(example = "senha-forte-123")
        @NotBlank
        String confirmPassword,

        @Schema(example = "+55 11 91234-5678")
        @Pattern(regexp = "\\+?[0-9 ()-]{8,20}", message = "Telefone inválido")
        String phone,

        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthDate,

        InvestorProfile investorProfile) {

    @Schema(hidden = true)
    @AssertTrue(message = "As senhas não coincidem")
    boolean isPasswordConfirmed() {
        return password == null || password.equals(confirmPassword);
    }

    /** O BCrypt só considera 72 bytes; um caractere acentuado ocupa mais de um. */
    @Schema(hidden = true)
    @AssertTrue(message = "A senha deve ter no máximo 72 bytes")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
