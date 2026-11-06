package br.com.chfb.ativo.predictiveengine.dto;

import java.math.BigDecimal;
import java.util.List;

public record SimulationResponse(
        Scenario scenario,
        int months,
        BigDecimal monthlyContribution,
        BigDecimal currentValue,
        BigDecimal totalContributions,
        double scenarioReturn,
        Assumptions assumptions,
        List<HistoryPoint> history,
        List<ProjectionPoint> projection,
        List<RiskAlert> alerts) {
}
