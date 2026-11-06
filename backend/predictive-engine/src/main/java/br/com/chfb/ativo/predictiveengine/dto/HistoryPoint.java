package br.com.chfb.ativo.predictiveengine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoryPoint(LocalDate date, BigDecimal value) {
}
