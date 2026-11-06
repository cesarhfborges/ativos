package br.com.chfb.ativo.assetmanagement.entity;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ajusta valores às colunas: {@code NUMERIC(18, 4)} para totais e taxas, {@code NUMERIC(24, 8)} para preços e quantidades. */
public final class Amounts {

    private static final int MONEY_SCALE = 4;
    private static final int MONEY_INTEGER_DIGITS = 14;
    private static final int PRICE_SCALE = 8;
    private static final int PRICE_INTEGER_DIGITS = 16;
    private static final int QUANTITY_SCALE = 8;
    private static final int QUANTITY_INTEGER_DIGITS = 16;

    private Amounts() {
    }

    public static BigDecimal money(BigDecimal value) {
        return fit(value, MONEY_SCALE, MONEY_INTEGER_DIGITS);
    }

    public static BigDecimal price(BigDecimal value) {
        return fit(value, PRICE_SCALE, PRICE_INTEGER_DIGITS);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return fit(value, QUANTITY_SCALE, QUANTITY_INTEGER_DIGITS);
    }

    /** Divide e arredonda na escala de preço, que é a usada pelo preço médio. */
    static BigDecimal priceQuotient(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal fit(BigDecimal value, int scale, int integerDigits) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        if (scaled.precision() - scale > integerDigits) {
            throw new BusinessRuleException("O valor excede o limite permitido");
        }
        return scaled;
    }
}
