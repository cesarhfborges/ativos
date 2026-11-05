# Etapa 3: Módulo Online de Cotações (market-tracker)

## Objetivo
Construir o módulo de coleta de preços de mercado independente, dinâmico e intercambiável, que mapeia apenas ativos em custódia ativa para consultar cotações e gerar snapshots periódicos a cada hora.

## Tarefas Técnicas

### 1. Arquitetura de Provedores (Strategy / Provider Pattern)
1. Definir interfaces em `market-tracker`:
   * `CryptoMarketProvider`: método `Map<String, BigDecimal> fetchCryptoPrices(Set<String> symbols);`
   * `StockMarketProvider`: método `Map<String, BigDecimal> fetchStockPrices(Set<String> symbols);`
2. Implementar a primeira integração real (ex: `BinanceMarketProvider` ou `CoinGeckoProvider` usando `WebClient`).
3. Suporte à troca de provedor via arquivo de configuração `application.yml`:
   * Uso de anotações `@ConditionalOnProperty` para instanciar a classe correspondente.

### 2. Mapeamento Inteligente de Ativos
1. Criar um serviço no módulo para identificar símbolos em aberto no sistema:
   * Consulta os ativos que possuem posições com `quantity > 0` entre todos os usuários.
   * Caso apenas BTC e ETH estejam cadastrados, apenas estes dois são repassados ao provedor, descartando todo o restante da base de mercado.
2. Gravar cotações recebidas na tabela `market_quotes` com timestamp atualizado.

### 3. Job Agendado (Hourly Poller)
1. Configurar `@EnableScheduling` no Spring Boot.
2. Criar método agendado `@Scheduled(cron = "0 0 * * * *")` (execução a cada hora cheia):
   * Executa a busca dos símbolos ativos.
   * Executa a busca de preços via provedor ativo.
   * Salva os registros atualizados.
   * Dispara rotina de geração de `portfolio_snapshots` calculando o valor consolidado de cada carteira no instante da cotação.

## Critérios de Aceite
* [ ] O provedor de cotações pode ser alternado alterando apenas uma propriedade de configuração, sem modificar o código-fonte da aplicação.
* [ ] Apenas os ativos que os usuários possuem em custódia são solicitados à API externa.
* [ ] O job de coleta roda a cada hora e gera os registros de snapshot patrimonial no banco de dados.
* [ ] Mecanismo de fallback caso a API externa retorne erro (mantendo última cotação conhecida e registrando warning no log).