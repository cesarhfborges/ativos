package br.com.chfb.ativo.sharedkernel.settings;

/** Chaves das configurações editáveis. Cada uma é também o nome da propriedade com o valor padrão. */
public final class SettingKeys {

    public static final String REGISTRATION_ENABLED = "app.registration-enabled";
    public static final String COLLECTION_ENABLED = "market.collection-enabled";
    public static final String MANUAL_REFRESH_SECONDS = "market.manual-refresh-seconds";
    public static final String SYNC_LOG_RETENTION_DAYS = "market.sync-log-retention-days";
    public static final String WATCHLIST_MAX_ITEMS = "watchlist.max-items";
    public static final String SIGNAL_OVERSOLD = "predictive.signal.oversold";
    public static final String SIGNAL_OVERBOUGHT = "predictive.signal.overbought";
    public static final String MIN_HISTORY_DAYS = "predictive.min-history-days";
    public static final String MIN_OBSERVED_RETURN = "predictive.min-observed-return";
    public static final String MAX_OBSERVED_RETURN = "predictive.max-observed-return";
    public static final String REFERENCE_STOCK_RETURN = "predictive.reference.stock-return";
    public static final String REFERENCE_STOCK_VOLATILITY = "predictive.reference.stock-volatility";
    public static final String REFERENCE_CRYPTO_RETURN = "predictive.reference.crypto-return";
    public static final String REFERENCE_CRYPTO_VOLATILITY = "predictive.reference.crypto-volatility";
    public static final String OPTIMISTIC_RETURN_BONUS = "predictive.scenarios.optimistic-return-bonus";
    public static final String PESSIMISTIC_RETURN_PENALTY = "predictive.scenarios.pessimistic-return-penalty";
    public static final String CRYPTO_STRESS = "predictive.scenarios.crypto-stress";
    public static final String STOCK_STRESS = "predictive.scenarios.stock-stress";
    public static final String MAX_CRYPTO_SHARE = "predictive.risk.max-crypto-share";
    public static final String MAX_ASSET_SHARE = "predictive.risk.max-asset-share";

    private SettingKeys() {
    }
}
