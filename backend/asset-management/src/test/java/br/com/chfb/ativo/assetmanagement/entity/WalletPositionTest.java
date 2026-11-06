package br.com.chfb.ativo.assetmanagement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletPositionTest {

    private final WalletPosition position = new WalletPosition(UUID.randomUUID(), null);

    @Test
    void primeiraCompraDefineOPrecoMedio() {
        position.addPurchase(new BigDecimal("2.5"), new BigDecimal("10.1234"), BigDecimal.ZERO);

        assertThat(position.getQuantity()).isEqualByComparingTo("2.5");
        assertThat(position.getAveragePrice()).isEqualTo(new BigDecimal("10.12340000"));
    }

    @Test
    void compraSeguinteRecalculaOPrecoMedioPonderado() {
        position.addPurchase(new BigDecimal("10"), new BigDecimal("20.00"), BigDecimal.ZERO);
        position.addPurchase(new BigDecimal("30"), new BigDecimal("40.00"), BigDecimal.ZERO);

        // (10 x 20 + 30 x 40) / 40
        assertThat(position.getQuantity()).isEqualByComparingTo("40");
        assertThat(position.getAveragePrice()).isEqualTo(new BigDecimal("35.00000000"));
    }

    @Test
    void precoMedioArredondaEmOitoCasasSemPerderPrecisaoNoCalculo() {
        position.addPurchase(new BigDecimal("0.00000001"), new BigDecimal("65000.1234"), BigDecimal.ZERO);
        position.addPurchase(new BigDecimal("0.00000002"), new BigDecimal("70000.5678"), BigDecimal.ZERO);

        // (65000.1234 + 2 x 70000.5678) / 3 = 68333.75300 (o double não representa esses valores)
        assertThat(position.getQuantity()).isEqualTo(new BigDecimal("0.00000003"));
        assertThat(position.getAveragePrice()).isEqualTo(new BigDecimal("68333.75300000"));
    }

    @Test
    void valoresGrandesNaoPerdemPrecisao() {
        position.addPurchase(new BigDecimal("9999999999.12345678"), new BigDecimal("12345678901.1234"), BigDecimal.ZERO);
        position.addPurchase(new BigDecimal("0.00000001"), new BigDecimal("0.0001"), BigDecimal.ZERO);

        assertThat(position.getQuantity()).isEqualTo(new BigDecimal("9999999999.12345679"));
        // A compra minúscula a preço baixo reduz a média em cerca de 0,000000012.
        assertThat(position.getAveragePrice()).isEqualTo(new BigDecimal("12345678901.12339999"));
    }

    @Test
    void vendaNaoAlteraOPrecoMedio() {
        position.addPurchase(new BigDecimal("10"), new BigDecimal("20.00"), BigDecimal.ZERO);

        position.removeSold(new BigDecimal("4"));

        assertThat(position.getQuantity()).isEqualByComparingTo("6");
        assertThat(position.getAveragePrice()).isEqualByComparingTo("20");
    }

    @Test
    void compraAposZerarRecomecaOPrecoMedio() {
        position.addPurchase(new BigDecimal("10"), new BigDecimal("20.00"), BigDecimal.ZERO);
        position.removeSold(new BigDecimal("10"));

        position.addPurchase(new BigDecimal("5"), new BigDecimal("50.00"), BigDecimal.ZERO);

        assertThat(position.getAveragePrice()).isEqualByComparingTo("50");
    }

    @Test
    void vendaAcimaDaCustodiaERecusadaSemAlterarAPosicao() {
        position.addPurchase(new BigDecimal("1"), new BigDecimal("20.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> position.removeSold(new BigDecimal("1.00000001")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(position.getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void quantidadeAcimaDaColunaERecusada() {
        position.addPurchase(new BigDecimal("9999999999999999"), new BigDecimal("1.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> position.addPurchase(new BigDecimal("1"), new BigDecimal("1.00"), BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
