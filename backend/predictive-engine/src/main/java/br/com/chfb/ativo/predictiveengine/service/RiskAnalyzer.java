package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.config.PredictiveProperties;
import br.com.chfb.ativo.predictiveengine.dto.RiskAlert;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Aponta concentração na carteira e calcula quanto precisaria sair para voltar ao limite. */
final class RiskAnalyzer {

    private static final Locale PT_BR = new Locale("pt", "BR");

    private RiskAnalyzer() {
    }

    static List<RiskAlert> analyze(List<AssetExposure> exposures, PredictiveProperties.Risk limits) {
        double total = exposures.stream().mapToDouble(exposure -> exposure.marketValue().doubleValue()).sum();
        if (total <= 0) {
            return List.of(new RiskAlert(
                    RiskAlert.Severity.INFO,
                    "Carteira vazia",
                    "Sem posições abertas, a análise de risco não se aplica.",
                    null));
        }

        List<RiskAlert> alerts = new ArrayList<>();
        double crypto = AssumptionsEstimator.sum(exposures, AssetClass.CRYPTO);
        if (crypto / total > limits.maxCryptoShare()) {
            alerts.add(new RiskAlert(
                    RiskAlert.Severity.WARNING,
                    "Alta exposição a criptoativos",
                    "%s da carteira está em criptoativos, acima do limite de referência de %s."
                            .formatted(percent(crypto / total), percent(limits.maxCryptoShare())),
                    "Para voltar ao limite, seria preciso reduzir cerca de %s em criptoativos ou aumentar as outras classes."
                            .formatted(money(crypto - limits.maxCryptoShare() * total))));
        }

        exposures.stream().max(Comparator.comparing(AssetExposure::marketValue)).ifPresent(largest -> {
            double value = largest.marketValue().doubleValue();
            if (value / total > limits.maxAssetShare()) {
                alerts.add(new RiskAlert(
                        RiskAlert.Severity.WARNING,
                        "Concentração em " + largest.symbol(),
                        "%s da carteira está em um único ativo, acima do limite de referência de %s."
                                .formatted(percent(value / total), percent(limits.maxAssetShare())),
                        exposures.size() == 1
                                ? "Considere diversificar em outros ativos."
                                : "Para voltar ao limite, seria preciso reduzir cerca de %s em %s ou aumentar os outros ativos."
                                        .formatted(money(value - limits.maxAssetShare() * total), largest.symbol())));
            }
        });

        if (alerts.isEmpty()) {
            alerts.add(new RiskAlert(
                    RiskAlert.Severity.INFO,
                    "Carteira dentro dos limites",
                    "Nenhuma concentração acima dos limites de referência foi encontrada.",
                    null));
        }
        return alerts;
    }

    private static String percent(double share) {
        NumberFormat format = NumberFormat.getPercentInstance(PT_BR);
        format.setMaximumFractionDigits(1);
        return format.format(share);
    }

    private static String money(double value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }
}
