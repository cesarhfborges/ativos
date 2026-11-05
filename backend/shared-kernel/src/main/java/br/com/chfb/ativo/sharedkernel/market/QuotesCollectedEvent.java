package br.com.chfb.ativo.sharedkernel.market;

import java.time.Instant;

/** Publicado ao fim da coleta agendada, com as cotações já gravadas. */
public record QuotesCollectedEvent(Instant collectedAt) {
}
