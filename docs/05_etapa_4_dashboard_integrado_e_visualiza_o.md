# Etapa 4: Dashboard Integrado e Visualização

## Objetivo
Integrar os dados contábeis do módulo `asset-management` com as cotações em tempo real do módulo `market-tracker` para compor o dashboard financeiro completo do usuário no Angular.

## Tarefas Técnicas

### 1. Endpoints de Agregação no Backend
1. Criar DTO e endpoint de visão consolidada (`GET /api/dashboard/overview`):
   * Valor total investido (baseado no Preço Médio).
   * Valor total a mercado (baseado na última cotação coletada em `market-tracker`).
   * Lucro/Prejuízo nominal total e percentual:
     $$\text{Retorno (\%)} = \frac{\text{Valor a Mercado} - \text{Valor Investido}}{\text{Valor Investido}} \times 100$$
2. Endpoint de detalhamento por ativo (`GET /api/dashboard/positions`):
   * Símbolo, tipo (Cripto/Ação), quantidade em carteira.
   * Preço Médio vs. Cotação Atual.
   * Lucro/Prejuízo individual por ativo.
3. Endpoint de histórico patrimonial (`GET /api/dashboard/history`):
   * Lista de pontos temporais obtidos da tabela `portfolio_snapshots`.

### 2. Interface de Dashboard no Angular
1. Criar cards superiores de resumo:
   * **Patrimônio Total** (Investimentos a valor de mercado).
   * **Total Investido** (baseado no Preço Médio).
   * **Desempenho Geral** (% de valorização ou depreciação com indicação visual verde/vermelho).
2. Construir tabela de ativos com indicadores de status de mercado.
3. Integrar biblioteca de gráficos (ex: Chart.js / ngx-charts):
   * Gráfico de linha demonstrando a evolução patrimonial ao longo do tempo (baseado nos snapshots horários/diários).
   * Gráfico de pizza com a distribuição de alocação da carteira (% Cripto vs. % Ações).

## Critérios de Aceite
* [ ] O dashboard reflete imediatamente oscilações nas cotações registradas pelo módulo `market-tracker`.
* [ ] Os cálculos de lucro/prejuízo mostram valores matematicamente consistentes com o preço médio e cotação de mercado.
* [ ] O gráfico de evolução patrimonial renderiza corretamente o histórico baseado em snapshots.