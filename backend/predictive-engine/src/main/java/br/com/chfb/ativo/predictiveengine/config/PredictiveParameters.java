package br.com.chfb.ativo.predictiveengine.config;

import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import br.com.chfb.ativo.sharedkernel.settings.SettingValues;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Parâmetros em vigor: o que o admin alterou e, no resto, o {@code application.yml}. O nível de confiança é fixo. */
@Component
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class PredictiveParameters {

    private final PredictiveProperties defaults;
    private final AppSettings appSettings;

    public PredictiveParameters(PredictiveProperties defaults, AppSettings appSettings) {
        this.defaults = defaults;
        this.appSettings = appSettings;
    }

    public PredictiveProperties current() {
        SettingValues values = appSettings.current();
        return new PredictiveProperties(
                values.getInt(SettingKeys.MIN_HISTORY_DAYS),
                values.getDouble(SettingKeys.MIN_OBSERVED_RETURN),
                values.getDouble(SettingKeys.MAX_OBSERVED_RETURN),
                defaults.confidenceLevel(),
                defaults.confidenceZ(),
                new PredictiveProperties.Reference(
                        values.getDouble(SettingKeys.REFERENCE_STOCK_RETURN),
                        values.getDouble(SettingKeys.REFERENCE_STOCK_VOLATILITY),
                        values.getDouble(SettingKeys.REFERENCE_CRYPTO_RETURN),
                        values.getDouble(SettingKeys.REFERENCE_CRYPTO_VOLATILITY)),
                new PredictiveProperties.Scenarios(
                        values.getDouble(SettingKeys.OPTIMISTIC_RETURN_BONUS),
                        values.getDouble(SettingKeys.PESSIMISTIC_RETURN_PENALTY),
                        values.getDouble(SettingKeys.CRYPTO_STRESS),
                        values.getDouble(SettingKeys.STOCK_STRESS)),
                new PredictiveProperties.Risk(
                        values.getDouble(SettingKeys.MAX_CRYPTO_SHARE),
                        values.getDouble(SettingKeys.MAX_ASSET_SHARE)));
    }
}
