package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.dto.ProjectionPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cálculo puro da curva, sem acesso a dados nem ao Spring. */
final class ProjectionCalculator {

    private static final int MONTHS_PER_YEAR = 12;

    private ProjectionCalculator() {
    }

    /**
     * Cada parcela (o valor inicial e cada contribuição) cresce pelo retorno composto até o mês k, e seu
     * intervalo abre com a raiz do tempo que ela ficou investida. Como todas seguem o mesmo mercado, os
     * limites das parcelas podem ser somados.
     */
    static List<ProjectionPoint> project(
            LocalDate start, double initialValue, double monthlyContribution, int months, double annualReturn, double annualVolatility, double z) {
        double monthlyGrowth = Math.pow(1 + annualReturn, 1.0 / MONTHS_PER_YEAR);
        List<ProjectionPoint> points = new ArrayList<>();
        for (int k = 0; k <= months; k++) {
            double expected = 0;
            double lower = 0;
            double upper = 0;
            for (int contribution = 0; contribution <= k; contribution++) {
                double amount = contribution == 0 ? initialValue : monthlyContribution;
                int investedMonths = k - contribution;
                double grown = amount * Math.pow(monthlyGrowth, investedMonths);
                double spread = z * annualVolatility * Math.sqrt((double) investedMonths / MONTHS_PER_YEAR);
                expected += grown;
                lower += grown * Math.exp(-spread);
                upper += grown * Math.exp(spread);
            }
            points.add(new ProjectionPoint(start.plusMonths(k), money(expected), money(lower), money(upper)));
        }
        return points;
    }

    static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
