package br.com.chfb.ativo.coreapi.service.admin;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import br.com.chfb.ativo.coreapi.dto.admin.AdminSettingsResponse;
import br.com.chfb.ativo.coreapi.dto.admin.SettingResponse;
import br.com.chfb.ativo.coreapi.dto.admin.SystemInfoResponse;
import br.com.chfb.ativo.coreapi.entity.AppSetting;
import br.com.chfb.ativo.coreapi.exception.AdminNotFoundException;
import br.com.chfb.ativo.coreapi.repository.AppSettingRepository;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingValues;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cada leitura vai ao banco: a tabela é mínima e a mudança precisa valer na hora. */
@Service
public class AppSettingsService implements AppSettings {

    private final AppSettingRepository repository;
    private final AdminAuditService audit;
    private final Environment environment;

    public AppSettingsService(AppSettingRepository repository, AdminAuditService audit, Environment environment) {
        this.repository = repository;
        this.audit = audit;
        this.environment = environment;
    }

    @Override
    public SettingValues current() {
        Map<String, String> overrides = overrides();
        return new SettingValues(key -> overrides.containsKey(key) ? overrides.get(key) : environment.getProperty(key));
    }

    @Transactional(readOnly = true)
    public AdminSettingsResponse describe() {
        Map<String, String> overrides = overrides();
        List<SettingResponse> settings = SettingsCatalog.ALL.stream()
                .map(definition -> {
                    String defaultValue = environment.getProperty(definition.key());
                    String override = overrides.get(definition.key());
                    return new SettingResponse(
                            definition.key(),
                            definition.group(),
                            definition.label(),
                            definition.description(),
                            definition.type(),
                            definition.min(),
                            definition.max(),
                            override == null ? defaultValue : override,
                            defaultValue,
                            override != null);
                })
                .toList();
        return new AdminSettingsResponse(settings, systemInfo());
    }

    @Transactional
    public AdminSettingsResponse update(Map<String, String> values, String actorEmail) {
        Instant now = Instant.now();
        values.forEach((key, value) -> {
            SettingsCatalog.Definition definition = definition(key);
            String normalized = normalize(definition, value);
            AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key));
            setting.change(normalized, actorEmail, now);
            repository.save(setting);
            audit.record(actorEmail, "SETTING_CHANGED", key, "Novo valor: " + normalized);
        });
        return describe();
    }

    @Transactional
    public AdminSettingsResponse restoreDefault(String key, String actorEmail) {
        definition(key);
        if (repository.existsById(key)) {
            repository.deleteById(key);
            audit.record(actorEmail, "SETTING_RESTORED", key, null);
        }
        repository.flush();
        return describe();
    }

    private Map<String, String> overrides() {
        return repository.findAll().stream().collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
    }

    private static SettingsCatalog.Definition definition(String key) {
        return SettingsCatalog.find(key).orElseThrow(() -> new AdminNotFoundException("Configuração desconhecida: " + key));
    }

    private static String normalize(SettingsCatalog.Definition definition, String value) {
        String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (SettingsCatalog.BOOLEAN.equals(definition.type())) {
            if (!text.equals("true") && !text.equals("false")) {
                throw new BusinessRuleException(definition.label() + ": informe verdadeiro ou falso");
            }
            return text;
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException(definition.label() + ": informe um número");
        }
        if (number.compareTo(definition.min()) < 0 || number.compareTo(definition.max()) > 0) {
            throw new BusinessRuleException(definition.label() + ": valor fora da faixa permitida");
        }
        if (SettingsCatalog.INTEGER.equals(definition.type())) {
            if (number.stripTrailingZeros().scale() > 0) {
                throw new BusinessRuleException(definition.label() + ": informe um número inteiro");
            }
            return number.toBigInteger().toString();
        }
        return number.toPlainString();
    }

    private SystemInfoResponse systemInfo() {
        return new SystemInfoResponse(
                environment.getProperty("market.provider.crypto", ""),
                environment.getProperty("market.provider.stock", ""),
                !environment.getProperty("market.brapi.token", "").isBlank(),
                environment.getProperty("market.collection-cron", ""),
                environment.getProperty("market.collect-on-startup", Boolean.class, false),
                environment.getProperty("predictive.enabled", Boolean.class, false));
    }
}
