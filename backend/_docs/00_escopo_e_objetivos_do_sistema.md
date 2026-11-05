# Sistema de Gestão de Ativos e Investimentos: Escopo e Objetivos

## 1. Visão Geral do Produto
O objetivo deste sistema é permitir que múltiplos usuários acompanhem e façam a gestão de suas carteiras de investimentos (com foco primário em **Ações** e **Criptomoedas**), visualizando patrimônio total atual, histórico de evolução, valorização/depreciação e cenários de projeção futura.

## 2. Metas de Negócio e Funcionais
* **Isolamento de Carteiras:** Cada usuário autenticado acessa exclusivamente seu próprio painel (dashboard), visualizando:
  * Saldo em caixa disponível (moeda fiat).
  * Posições abertas em ativos (Ações e Criptos).
  * Patrimônio consolidado (Caixa + Soma de `Quantidade * Cotação Atual`).
  * Rentabilidade absoluta e percentual com base no Preço Médio ponderado de compra.
* **Operações Transacionais:**
  * Compra de ativos: liquida do saldo em caixa e cria/incrementa a posição de custódia com recálculo de preço médio.
  * Venda de ativos: reduz/encerra a posição de custódia e injeta o montante liquidado de volta ao caixa.
  * Aportes e retiradas diretas de caixa.
* **Coleta Inteligente de Cotações:**
  * Coleta periódica horária automática.
  * Consulta apenas os ativos que os usuários possuem em custódia ativa (evitando requisições desnecessárias para ativos não utilizados).
* **Projeção de Carteira:**
  * Módulo apartado com inteligência de análise de tendências e cenários futuros (curto, médio e longo prazo).

## 3. Diretrizes de Arquitetura e Limites
* **Stack Principal:** Spring Boot (Java) no backend e Angular no frontend.
* **Banco de Dados:** PostgreSQL único.
* **Autenticação:** Stateful via sessão de usuário (`JSESSIONID`), suportando concorrência multiusuário.
* **Design de Módulos (Monólito Modular):**
  * Toda a solução roda no mesmo projeto Spring Boot, mas separada em pacotes/submódulos independentes com interfaces de contrato bem definidas.
  * Módulos previstos:
    1. `core-api`: Autenticação por sessão, segurança e orquestração base.
    2. `asset-management`: Regras de negócio de carteira, saldo em caixa, compra, venda e preço médio.
    3. `market-tracker`: Mecanismo dinâmico de coleta de cotações com suporte a troca simples de provedores (Binance, Brapi, CoinGecko, etc.).
    4. `predictive-engine`: Motor preditivo de simulações e cálculos de tendências.
    5. `shared-kernel`: Contratos, identificadores comuns e enums compartilhados.