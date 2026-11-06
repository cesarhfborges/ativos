package br.com.chfb.ativo.coreapi.service.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Define o primeiro administrador. A conta precisa existir: crie-a pela tela de registro e reinicie. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AdminUserService adminUserService;
    private final String adminEmail;

    public AdminBootstrap(AdminUserService adminUserService, @Value("${app.admin-email:}") String adminEmail) {
        this.adminUserService = adminUserService;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminEmail.isBlank()) {
            adminUserService.promoteAtStartup(adminEmail);
        }
    }
}
