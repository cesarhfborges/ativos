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
     * A taxa faz parte do custo: o preço médio é quanto cada unidade custou de fato.
       $$\text{Preço Médio} = \frac{(\text{Qtd Atual} \times \text{PM Atual}) + (\text{Qtd Comprada} \times \text{Preço Compra}) + \text{Taxa}}{\text{Qtd Atual} + \text{Qtd Comprada}}$$
     * Em posição nova ou zerada, a quantidade atual é zero e a mesma fórmula vale.
   * Registrar a entrada em `transactions`.
   * **Verificação do símbolo:** no primeiro cadastro, consultar o provedor de cotações do tipo escolhido:
     * Encontrado: o ativo nasce como `VERIFIED`.
     * Não encontrado, ou encontrado apenas no outro tipo: a compra responde `422` com o código `SYMBOL_NOT_FOUND` e, se houver, o `suggestedType`. A tela oferece trocar o tipo ou registrar mesmo assim (`acceptUnverified`), e o ativo nasce como `UNVERIFIED`.
     * Provedor sem resposta: o ativo nasce como `UNVERIFIED`, sem recusa. Só um "não existe" explícito conta: sem token, a Brapi responde `401` até para ações reais.
     * O catálogo é compartilhado: quem corrige um cadastro errado é o admin (Etapa 6).
2. **Operação de Venda (`SELL`):**
   * Validar se a posição do ativo existe e se `quantidade_em_custodia >= quantity_venda`.
   * Subtrair a quantidade em `wallet_positions` (se zerar, manter registro com `quantity = 0` ou excluir).
   * Calcular o valor líquido da operação: $(\text{quantity} \times \text{unit\_price}) - \text{fee}$.
   * Registrar a transação de liquidação.

### 3. Exposição de APIs e Telas (Angular)
1. Endpoints:
   * `GET /api/transactions`: histórico de compras e vendas, com filtros e paginação no formato do `simplePaginate` do Laravel.
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