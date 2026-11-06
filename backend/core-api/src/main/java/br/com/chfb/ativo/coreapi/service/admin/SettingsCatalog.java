package br.com.chfb.ativo.coreapi.service.admin;

import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** O que o admin pode ajustar, com as faixas aceitas. O que muda em tempo real fica aqui; segredos nunca. */
final class SettingsCatalog {

    static final String BOOLEAN = "BOOLEAN";
    static final String INTEGER = "INTEGER";
    static final String PERCENT = "PERCENT";

    private static final String ACCESS = "Acesso";
    private static final String COLLECTION = "Coleta de cotações";
    private static final String WATCHLIST = "Acompanhamento";
    private static final String PROJECTION = "Projeções";
    private static final String REFERENCE = "Premissas de referência";
    private static final String SCENARIOS = "Cenários";
    private static final String RISK = "Limites de risco";
    private static final String SIGNAL = "Sinal técnico";

    static final List<Definition> ALL = List.of(
            bool(SettingKeys.REGISTRATION_ENABLED, ACCESS, "Cadastro público", "Permite criar conta pela tela de registro. O admin sempre pode criar contas."),
            bool(SettingKeys.COLLECTION_ENABLED, COLLECTION, "Coleta automática", "Pausada, não há novas cotações agendadas nem snapshots, e um símbolo novo espera a retomada. O admin ainda pode coletar pela tela de sincronização."),
            integer(SettingKeys.MANUAL_REFRESH_SECONDS, COLLECTION, "Intervalo da coleta pelo admin (segundos)", "Tempo mínimo entre coletas pedidas na tela de sincronização, que consomem a cota das APIs externas.", 10, 3600),
            integer(SettingKeys.SYNC_LOG_RETENTION_DAYS, COLLECTION, "Retenção dos registros de sincronização (dias)", "Registros mais antigos são apagados depois de cada coleta agendada.", 7, 3650),
            integer(SettingKeys.WATCHLIST_MAX_ITEMS, WATCHLIST, "Ativos acompanhados por usuário", "Só protege a cota das APIs externas contra abuso: um mesmo ativo é consultado uma vez, não importa quantos usuários o acompanhem.", 1, 1000),
            integer(SettingKeys.MIN_HISTORY_DAYS, PROJECTION, "Histórico mínimo por ativo (dias)", "Com menos dias de cotações, o ativo usa as premissas de referência.", 7, 365),
            percent(SettingKeys.MIN_OBSERVED_RETURN, PROJECTION, "Retorno anual medido: limite inferior", "Evita valores absurdos quando há pouco histórico.", "-1", "0"),
            percent(SettingKeys.MAX_OBSERVED_RETURN, PROJECTION, "Retorno anual medido: limite superior", "Evita valores absurdos quando há pouco histórico.", "0", "5"),
            percent(SettingKeys.REFERENCE_STOCK_RETURN, REFERENCE, "Retorno anual de ações", "Premissa configurada, não é dado de mercado.", "-0.5", "1"),
            percent(SettingKeys.REFERENCE_STOCK_VOLATILITY, REFERENCE, "Volatilidade anual de ações", "Premissa configurada, não é dado de mercado.", "0", "2"),
            percent(SettingKeys.REFERENCE_CRYPTO_RETURN, REFERENCE, "Retorno anual de cripto", "Premissa configurada, não é dado de mercado.", "-0.5", "2"),
            percent(SettingKeys.REFERENCE_CRYPTO_VOLATILITY, REFERENCE, "Volatilidade anual de cripto", "Premissa configurada, não é dado de mercado.", "0", "3"),
            percent(SettingKeys.OPTIMISTIC_RETURN_BONUS, SCENARIOS, "Bônus do cenário otimista", "Pontos percentuais somados ao retorno anual.", "0", "1"),
            percent(SettingKeys.PESSIMISTIC_RETURN_PENALTY, SCENARIOS, "Penalidade do cenário pessimista", "Pontos percentuais subtraídos do retorno anual.", "0", "1"),
            percent(SettingKeys.CRYPTO_STRESS, SCENARIOS, "Queda imediata em cripto", "Aplicada no cenário pessimista, se houver cripto na carteira.", "0", "1"),
            percent(SettingKeys.STOCK_STRESS, SCENARIOS, "Queda imediata em ações", "Aplicada no cenário pessimista, se não houver cripto na carteira.", "0", "1"),
            percent(SettingKeys.MAX_CRYPTO_SHARE, RISK, "Exposição máxima a cripto", "Acima disso, a tela de projeções mostra um alerta.", "0", "1"),
            percent(SettingKeys.MAX_ASSET_SHARE, RISK, "Concentração máxima em um ativo", "Acima disso, a tela de projeções mostra um alerta.", "0", "1"),
            integer(SettingKeys.SIGNAL_OVERSOLD, SIGNAL, "RSI sobrevendido", "Com o RSI neste valor ou abaixo, o ativo aparece como sobrevendido no Acompanhamento.", 5, 45),
            integer(SettingKeys.SIGNAL_OVERBOUGHT, SIGNAL, "RSI sobrecomprado", "Com o RSI neste valor ou acima, o ativo aparece como sobrecomprado no Acompanhamento.", 55, 95));

    private SettingsCatalog() {
    }

    static Optional<Definition> find(String key) {
        return ALL.stream().filter(definition -> definition.key().equals(key)).findFirst();
    }

    private static Definition bool(String key, String group, String label, String description) {
        return new Definition(key, group, label, description, BOOLEAN, null, null);
    }

    private static Definition integer(String key, String group, String label, String description, int min, int max) {
        return new Definition(key, group, label, description, INTEGER, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    private static Definition percent(String key, String group, String label, String description, String min, String max) {
        return new Definition(key, group, label, description, PERCENT, new BigDecimal(min), new BigDecimal(max));
    }

    record Definition(String key, String group, String label, String description, String type, BigDecimal min, BigDecimal max) {
    }
}
