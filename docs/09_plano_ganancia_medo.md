# Plano: Índice de Ganância e Medo

Situação: **implementado**. As regras em vigor estão na seção "Ganância e medo" do `README.md`; este documento guarda o motivo das decisões.

## 1. O Problema
O Painel descrevia a carteira, mas não mostrava se os preços dos ativos dela estão em euforia ou em pânico. Os índices conhecidos dependem de dados que o sistema não tem: volume, opções, redes sociais e buscas. Só há preços de fechamento.

## 2. Decisões
* **O índice descreve a carteira, não o mercado.** Um índice "do mercado" dependeria dos símbolos que os usuários acompanham e mudaria sempre que alguém adicionasse um ativo.
* **Calcular na coleta de hora em hora**, ouvindo `QuotesCollectedEvent`. É quando os preços mudam, sem agenda nova nem chamada a provedores.
* **Não calcular na compra e venda.** A operação não muda preços, a data pode ser retroativa, um símbolo novo ainda não tem histórico e um estorno deixaria o valor errado.
* **Não calcular em horário específico.** Cripto negocia 24 horas e a B3 não, então nenhum horário serve aos dois, e a leitura ficaria até um dia defasada.
* **Não calcular tudo na leitura.** Repetiria o mesmo trabalho para todos os usuários, a cada troca de período, com dados que só mudam de hora em hora.
* **Gravar por símbolo e aplicar os pesos na leitura.** Valores gravados por carteira ficam errados com estornos e lançamentos retroativos, como já acontece com os snapshots, que precisam do `ReversalHistoryCorrector`.
* **A falha do cálculo não sobe para a coleta.** O evento é síncrono e o publicador não trata erros: uma exceção impediria os snapshots e a limpeza do registro de coletas.

## 3. Fora de escopo
* **Índice externo.** O da alternative.me é só de Bitcoin, não cobre a B3, tem uma das fontes marcada como pausada e exige atribuição ao lado do dado. Seus termos completos precisariam ser lidos antes de adotá-lo.
* **Separar cripto e ações no gráfico.** Fácil de acrescentar depois, porque os dados já ficam por símbolo.

## 4. Pendências
* Calibrar pesos e limites. Com preços reais de BTC, ETH, PETR4 e VALE3, as notas ficaram entre 9 e 83, mas três dos quatro componentes têm correlação perto de 0,9 e pesam a tendência recente.
