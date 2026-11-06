package br.com.chfb.ativo.assetmanagement.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketAssetResponseTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static PricePoint point(int day, String price) {
        return new PricePoint(T0.plusSeconds(day * 86_400L), new BigDecimal(price));
    }

    @Test
    void calculaVariacaoEFaixaTerminandoNaUltimaCotacao() {
        Quote quote = new Quote("ABC", new BigDecimal("120"), T0.plusSeconds(5 * 86_400L));

        MarketAssetResponse asset = MarketAssetResponse.of(
                "ABC", quote, new BigDecimal("150"), List.of(point(0, "100"), point(1, "80"), point(2, "150")));

        assertThat(asset.periodChangePercent()).isEqualByComparingTo("20");
        assertThat(asset.change24hPercent()).isEqualByComparingTo("-20");
        assertThat(asset.periodLow()).isEqualByComparingTo("80");
        assertThat(asset.periodHigh()).isEqualByComparingTo("150");
        assertThat(asset.prices()).hasSize(4);
        assertThat(asset.prices().get(3).price()).isEqualByComparingTo("120");
    }

    @Test
    void naoRepeteOPontoDaColetaQueGerouACotacao() {
        Quote quote = new Quote("ABC", new BigDecimal("150"), point(1, "150").time());

        MarketAssetResponse asset = MarketAssetResponse.of("ABC", quote, null, List.of(point(0, "100"), point(1, "150")));

        assertThat(asset.prices()).hasSize(2);
    }

    @Test
    void semPrecosSuficientesDeixaOsCamposNulos() {
        MarketAssetResponse withoutData = MarketAssetResponse.of("ABC", null, null, List.of());
        assertThat(withoutData.change24hPercent()).isNull();
        assertThat(withoutData.periodChangePercent()).isNull();
        assertThat(withoutData.periodLow()).isNull();
        assertThat(withoutData.periodHigh()).isNull();

        Quote quote = new Quote("ABC", new BigDecimal("10"), T0);
        MarketAssetResponse onePrice = MarketAssetResponse.of("ABC", quote, null, List.of());
        assertThat(onePrice.periodChangePercent()).isNull();
        assertThat(onePrice.change24hPercent()).isNull();
        assertThat(onePrice.periodLow()).isEqualByComparingTo("10");
    }

    @Test
    void precoAnteriorZeradoNaoGeraDivisaoPorZero() {
        Quote quote = new Quote("ABC", new BigDecimal("10"), point(1, "10").time());

        MarketAssetResponse asset = MarketAssetResponse.of("ABC", quote, BigDecimal.ZERO, List.of(point(0, "0")));

        assertThat(asset.change24hPercent()).isNull();
        assertThat(asset.periodChangePercent()).isNull();
    }
}
