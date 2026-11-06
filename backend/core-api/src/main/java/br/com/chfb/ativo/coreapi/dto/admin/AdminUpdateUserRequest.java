package br.com.chfb.ativo.coreapi.dto.admin;

import br.com.chfb.ativo.coreapi.entity.InvestorProfile;
import br.com.chfb.ativo.coreapi.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AdminUpdateUserRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Pattern(regexp = "\\+?[0-9 ()-]{8,20}", message = "Telefone inválido")
        String phone,

        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthDate,

        InvestorProfile investorProfile,

        @NotNull
        Role role) {
}
