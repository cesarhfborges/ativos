package br.com.chfb.ativo.predictiveengine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectionPoint(LocalDate date, BigDecimal expected, BigDecimal lower, BigDecimal upper) {
}
