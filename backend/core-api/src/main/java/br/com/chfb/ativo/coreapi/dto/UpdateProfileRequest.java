package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.coreapi.entity.InvestorProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** O e-mail fica de fora: é a identidade de login e não há como verificar um endereço novo. */
public record UpdateProfileRequest(
        @Schema(example = "Ana")
        @NotBlank
        @Size(max = 120)
        String name,

        @Schema(example = "+55 11 91234-5678")
        @Pattern(regexp = "\\+?[0-9 ()-]{8,20}", message = "Telefone inválido")
        String phone,

        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthDate,

        InvestorProfile investorProfile) {
}
