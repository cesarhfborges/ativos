package br.com.chfb.ativo.coreapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentação servida em {@code /swagger-ui.html}. Desligue com {@code SWAGGER_ENABLED=false}. */
@Configuration
public class OpenApiConfig {

    public static final String AUTH_TAG = "Autenticação";
    public static final String TRANSACTION_TAG = "Transações";
    public static final String PROFILE_TAG = "Perfil";
    public static final String ASSET_TAG = "Ativos";
    public static final String DASHBOARD_TAG = "Dashboard";
    public static final String PREDICTION_TAG = "Projeções";
    public static final String SESSION_SCHEME = "session";

    private static final String DESCRIPTION = """
            A autenticação usa sessão por cookie e todo POST exige o token CSRF.

            Para testar nesta página: execute `GET /api/auth/csrf` e depois `POST /api/auth/login`. \
            O navegador guarda os cookies e a página envia o header `X-XSRF-TOKEN` sozinha, \
            então não é preciso usar o botão Authorize.
            """;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ativos API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }

    /** O logout é um filtro do Spring Security, então o springdoc não o encontra sozinho. */
    @Bean
    public OpenApiCustomizer logoutEndpointCustomizer() {
        return openApi -> openApi.path("/api/auth/logout", new PathItem().post(new Operation()
                .tags(List.of(AUTH_TAG))
                .summary("Encerra a sessão")
                .operationId("logout")
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Sessão encerrada")))));
    }
}
