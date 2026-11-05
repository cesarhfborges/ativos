# Etapa 2: Módulo de Gestão de Ativos (asset-management)

## Objetivo
Implementar o core transacional de custódia do sistema: cadastro de ativos (Ações e Cripto), registros de compra/venda com histórico e cálculo contínuo do preço médio ponderado.

## Tarefas Técnicas

### 1. Modelo de Domínio e Persistência
1. Criar migração Flyway para as tabelas:
   * `wallets`, `assets`, `wallet_positions`, `transactions`.
2. Criar entidades JPA e Repositories correspondentes no módulo `asset-management`.

### 2. Casos de Uso e Regras de Negócio
1. **Operação de Compra (`BUY`):**
   * Atualizar a posição em `wallet_positions`:
     * Se nova: cria posição com `average_price = unit_price`.
     * Se existente: calcula novo preço médio:
       $$\text{Preço Médio} = \frac{(\text{Qtd Atual} \times \text{PM Atual}) + (\text{Qtd Comprada} \times \text{Preço Compra})}{\text{Qtd Atual} + \text{Qtd Comprada}}$$
   * Registrar a entrada em `transactions`.
2. **Operação de Venda (`SELL`):**
   * Validar se a posição do ativo existe e se `quantidade_em_custodia >= quantity_venda`.
   * Subtrair a quantidade em `wallet_positions` (se zerar, manter registro com `quantity = 0` ou excluir).
   * Calcular o valor líquido da operação: $(\text{quantity} \times \text{unit\_price}) - \text{fee}$.
   * Registrar a transação de liquidação.

### 3. Exposição de APIs e Telas (Angular)
1. Endpoints:
   * `GET /api/transactions`: histórico de compras e vendas, com filtro por tipo.
   * `POST /api/transactions/buy`: registrar compra de ativo.
   * `POST /api/transactions/sell`: registrar venda/liquidação de ativo.
2. Componentes no Angular:
   * Tabela de posições atuais com quantidade e preço médio.
   * Formulário de compra/venda de ativos com validação de quantidade disponível.
   * Extrato histórico de transações.

## Critérios de Aceite
* [ ] Nenhuma operação de venda pode ser concluída com custódia insuficiente.
* [ ] Recálculo do preço médio validado por testes unitários com valores decimais de alta precisão (`BigDecimal`).
* [ ] Usuário consegue comprar BTC/Ações, vender uma parte e ver a posição e o histórico atualizados.