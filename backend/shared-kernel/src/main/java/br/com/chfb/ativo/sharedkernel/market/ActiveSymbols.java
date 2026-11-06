package br.com.chfb.ativo.sharedkernel.market;

import java.util.Set;

/** Símbolos em alguma carteira ou acompanhados por algum usuário: são os únicos consultados nos provedores. */
public record ActiveSymbols(Set<String> crypto, Set<String> stocks) {
}
