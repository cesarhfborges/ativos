package br.com.chfb.ativo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Fica no pacote raiz para que o scan de componentes, entidades e repositórios
 * alcance todos os módulos ({@code br.com.chfb.ativo.*}).
 *
 * <p>Estende {@link SpringBootServletInitializer} para o deploy do WAR em um Tomcat externo.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AtivosApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(AtivosApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AtivosApplication.class);
    }
}
