# Plano: Suporte a Múltiplas Moedas

Situação: **proposta, ainda não implementada**. Hoje todos os valores são tratados como BRL.

## 1. O Problema
Ações e criptos são negociadas em moedas diferentes: AAPL em USD, SAP em EUR, PETR4 em BRL, BTC em USDT, USD ou BRL. Hoje o sistema assume BRL em toda parte, o que gera erros silenciosos:

* Um preço em USD digitado é somado como se fosse BRL, e os totais do painel ficam errados.
* O par da Binance é fixo em `BTCBRL`, e a Brapi só cobre a B3.
* `assets.symbol` é único globalmente, então o mesmo ticker em duas bolsas não cabe no modelo.
* Lucro em moeda estrangeira mistura duas coisas diferentes: a variação do ativo e a variação do câmbio.

## 2. Conceitos
* **Moeda do ativo:** em que ele é negociado. Vem da bolsa ou do par.
* **Moeda da operação:** em que aquela compra ou venda aconteceu. Normalmente igual à do ativo.
* **Moeda base:** em que o usuário vê o patrimônio. É um ajuste por carteira.

A regra central: **guardar sempre o valor original e a taxa de câmbio da data da operação**. Nunca sobrescrever o valor original com o convertido.

## 3. Modelo de Dados

| Tabela | Mudança |
| --- | --- |
| `wallets` | `base_currency CHAR(3) NOT NULL DEFAULT 'BRL'` |
| `assets` | `currency CHAR(3) NOT NULL` e `exchange VARCHAR(20)`; unicidade passa a ser `(symbol, exchange)` |
| `transactions` | `currency CHAR(3) NOT NULL` e `fx_rate NUMERIC(24, 10) NOT NULL` (moeda da operação para a base) |
| `wallet_positions` | `average_price` continua na moeda do ativo; novo `average_cost_base NUMERIC(18, 4)` com o custo médio já convertido |
| `market_quotes` | `currency CHAR(3) NOT NULL` |
| `fx_rates` (nova) | `base`, `quote`, `rate NUMERIC(24, 10)`, `rate_date`, única por par e data |
| `portfolio_snapshots` | Valores gravados na moeda base, com `base_currency` para detectar uma troca de base |

Códigos ISO 4217 para moedas fiduciárias. USDT e USDC ficam como moedas próprias, sem equivaler a USD: a paridade é próxima, mas não garantida.

Cada moeda tem suas casas decimais (JPY tem zero, BRL e USD têm duas), então a formatação precisa vir da moeda, e não de uma escala fixa. As colunas continuam com quatro casas, que atendem a todas.

## 4. Cálculos

Compra de 10 AAPL a US$ 200, com câmbio de 5,00:

* Preço médio na moeda do ativo: US$ 200,00.
* Custo na base: 10 × 200 × 5,00 = R$ 10.000,00.

Venda de 4 AAPL a US$ 250, com câmbio de 5,50:

| Componente | Fórmula | Valor |
| --- | --- | --- |
| Valor líquido na base | 4 × 250 × 5,50 | R$ 5.500,00 |
| Custo na base | 4 × 200 × 5,00 | R$ 4.000,00 |
| **Lucro realizado** | Diferença | **R$ 1.500,00** |
| Parte do ativo | 4 × (250 − 200) × 5,00 | R$ 1.000,00 |
| Parte do câmbio | 4 × 250 × (5,50 − 5,00) | R$ 500,00 |

Separar as duas partes é o maior ganho para o usuário: mostra se o lucro veio do ativo ou do câmbio.

* **Posições abertas:** convertidas pela cotação atual e pelo câmbio atual.
* **Preço médio:** calculado separadamente na moeda do ativo e na base, porque cada compra tem seu câmbio.
* **Projeções:** rodam na moeda base e herdam o risco cambial pela volatilidade dos snapshots. Com valores de referência, ele não é considerado, e a tela precisa avisar.

## 5. Provedores
1. **Câmbio:** nova interface `FxRateProvider`, no mesmo padrão dos provedores atuais. Sugestão: Frankfurter (dados do BCE, gratuito, sem token) ou PTAX do Banco Central. A coleta diária grava em `fx_rates`.
2. **Cripto:** o par passa a ser `símbolo + moeda`. Quando a Binance não tiver o par direto, usar USDT e converter.
3. **Ações:** a interface ganha a moeda ou bolsa, e o provedor passa a ser escolhido por bolsa: Brapi para a B3 e outro para EUA e Europa, a escolher (Twelve Data e Finnhub são candidatos; plano gratuito e limites precisam ser verificados).
4. **Falha de câmbio:** mesma regra das cotações, com a última taxa conhecida e aviso no log. A tela mostra a data da taxa em uso.

## 6. Frontend
* **Compra:** seletor de moeda, com padrão vindo do ativo, e campo de câmbio preenchido pela taxa do dia, editável, porque a taxa real da corretora costuma ser outra.
* **Venda:** moeda fixa pela posição, com câmbio editável.
* **Tabela de posições:** preço na moeda do ativo, e valor e lucro na moeda base.
* **Painel:** ajuste da moeda base e detalhamento de lucro entre ativo e câmbio.
* **Formatação:** os valores fixos em `BRL` nos pipes e no `p-inputnumber` passam a usar a moeda de cada dado. O idioma continua pt-BR.

## 7. Fases

| Fase | Entrega | Resultado |
| --- | --- | --- |
| 1 | Colunas de moeda, tudo em BRL e com `fx_rate = 1` | Sem mudança visível; deixa o modelo pronto |
| 2 | `fx_rates`, provedor de câmbio e conversão no painel | Carteira em mais de uma moeda com totais corretos |
| 3 | Moeda e câmbio nos formulários, com lucro separado | Registro de operações internacionais completo |
| 4 | Provedor por bolsa e par de cripto por moeda | Cotações automáticas fora da B3 |
| 5 | Troca da moeda base, recalculando o histórico | Ver a carteira em outra moeda |

A fase 1 é a mais barata, e fica mais cara a cada operação registrada: dados antigos sem moeda precisam ser migrados assumindo uma.

## 8. Decisões em Aberto
1. **Moeda base por carteira ou por usuário?** Sugestão: por carteira, que já é única por usuário e permite várias no futuro.
2. **Trocar a moeda base reescreve os snapshots?** Sugestão: não; converter na leitura pelas taxas históricas.
3. **Mesmo ativo em duas bolsas conta como um só?** Sugestão: não; cada listagem é um ativo, como já funciona nas corretoras.
4. **Quais mercados são prioridade?** Definem o provedor de ações e o custo mensal da fase 4.

## 9. Fora de Escopo
Cálculo de imposto de renda (a regra brasileira tem custo em BRL pela PTAX e isenções próprias), dividendos e desdobramentos de ações.
