package br.com.chfb.ativo.coreapi.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chfb.ativo.assetmanagement.service.AssetCatalogService;
import br.com.chfb.ativo.assetmanagement.service.DashboardService;
import br.com.chfb.ativo.assetmanagement.service.MarketOverviewService;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.assetmanagement.service.WatchlistService;
import br.com.chfb.ativo.markettracker.service.SyncLog;
import br.com.chfb.ativo.sharedkernel.market.AssetSearch;
import br.com.chfb.ativo.predictiveengine.service.PredictionService;
import br.com.chfb.ativo.coreapi.service.AuthService;
import br.com.chfb.ativo.coreapi.service.ProfileService;
import br.com.chfb.ativo.coreapi.service.admin.AdminAuditService;
import br.com.chfb.ativo.coreapi.service.admin.AdminOperationsService;
import br.com.chfb.ativo.coreapi.service.admin.AdminUserService;
import br.com.chfb.ativo.coreapi.service.admin.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private MarketOverviewService marketOverviewService;

    @MockitoBean
    private SyncLog syncLog;

    @MockitoBean
    private WatchlistService watchlistService;

    @MockitoBean
    private PredictionService predictionService;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AdminOperationsService adminOperationsService;

    @MockitoBean
    private AppSettingsService appSettingsService;

    @MockitoBean
    private AdminAuditService adminAuditService;

    @MockitoBean
    private AssetCatalogService assetCatalogService;

    @MockitoBean
    private AssetSearch assetSearch;

    @Test
    void rotaProtegidaRetorna401SemRedirecionar() throws Exception {
        mockMvc.perform(get("/api/qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void postSemTokenCsrfERecusado() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPermiteCredenciaisApenasParaOrigemConfigurada() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
