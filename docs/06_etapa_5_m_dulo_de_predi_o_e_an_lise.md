# Etapa 5: Módulo de Predição e Análise (predictive-engine)

## Objetivo
Implementar o motor de predição e projeções futuras como um módulo desacoplado, permitindo ao usuário simular cenários patrimoniais com base nos aportes médios, volatilidade e tendências históricas.

## Tarefas Técnicas

### 1. Regras de Predição e Modelos Analíticos
1. No módulo `predictive-engine`, implementar classes de análise sem dependência direta de detalhes transacionais de compra/venda:
   * Consumo dos snapshots históricos de patrimônio e do histórico de cotações.
2. Modelos de Projeção a implementar:
   * **Projeção por Aporte Constante:** Simulação de crescimento patrimonial com taxa média observada e aportes regulares programados.
   * **Análise de Cenários:**
     * Cenário Otimista (+X% sobre média de mercado).
     * Cenário Realista (tendência linear / média móvel).
     * Cenário Pessimista/Estresse (-Y% de queda na classe de risco mais alta, ex: Cripto).
   * **Alerta de Risco/Concentração:** Identificação de carteira desbalanceada (ex: mais de 70% exposta a criptoativos de alta volatilidade).

### 2. Endpoints e Parâmetros de Simulação
1. Criar endpoint `POST /api/predictions/simulate`:
   * Payload de entrada: `mesesProjecao` (6, 12, 24, 60), `aporteMensalEstimado`, `perfilCenario (OTIMISTA, REALISTA, PESSIMISTA)`.
   * Retorno: Série temporal com a curva patrimonial projetada e intervalo de confiança.

### 3. Interface de Projeção no Angular
1. Criar tela dedicada: `/projecoes`.
2. Controles interativos para o usuário ajustar:
   * Slider ou seletor de horizonte de tempo (meses/anos).
   * Campo de valor estimado de novos aportes.
3. Gráfico de linha comparativa:
   * Curva real até a data atual.
   * Linha pontilhada com a projeção futura calculada pelo módulo.
4. Painel com resumo dos riscos e sugestão de rebalanceamento de carteira.

## Critérios de Aceite
* [ ] O módulo funciona isoladamente e pode ser desativado ou reescrito sem afetar as rotinas de gestão de ativos e cotações online.
* [ ] O endpoint de simulação responde com os cenários calculados respeitando os parâmetros do usuário.
* [ ] O gráfico de projeção no Angular plota as linhas de tendência futura de forma legível e responsiva.