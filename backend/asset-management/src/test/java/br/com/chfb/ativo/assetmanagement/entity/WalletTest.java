package br.com.chfb.ativo.assetmanagement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTest {

    private final Wallet wallet = new Wallet(UUID.randomUUID(), "Teste");

    @Test
    void creditoEDebitoAtualizamOSaldo() {
        wallet.credit(new BigDecimal("100.10"));
        wallet.debit(new BigDecimal("0.20"));

        assertThat(wallet.getFiatBalance()).isEqualTo(new BigDecimal("99.9000"));
    }

    @Test
    void podeDebitarExatamenteOSaldo() {
        wallet.credit(new BigDecimal("10"));

        wallet.debit(new BigDecimal("10"));

        assertThat(wallet.getFiatBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debitoAcimaDoSaldoERecusadoSemAlterarOSaldo() {
        wallet.credit(new BigDecimal("10"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("10.0001")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(wallet.getFiatBalance()).isEqualByComparingTo("10");
    }

    @Test
    void saldoAcimaDaColunaERecusado() {
        wallet.credit(new BigDecimal("99999999999999.9999"));

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("0.0001")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
