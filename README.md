# Sistema de Gestão de Ativos e Investimentos

Sistema multiusuário para acompanhar carteiras de ações e criptomoedas: patrimônio, rentabilidade, histórico e projeções. O escopo completo está em [`docs/`](docs/).

O repositório contém autenticação, registro de compras e vendas, coleta de cotações, dashboard, projeções, administração e o frontend que usa esses recursos. Todos os valores são tratados como BRL; o suporte a outras moedas está planejado em [`docs/07_plano_multimoeda.md`](docs/07_plano_multimoeda.md).

## Stack

- Java 17 e Spring Boot 3.5, empacotado como WAR para Tomcat 10.1
- PostgreSQL 16, Flyway e Spring Session JDBC
- springdoc-openapi (Swagger UI)
- Maven (multi-módulo), Docker e Docker Compose
- Angular 21 e PrimeNG 21 (layout Sakai), com Node 22, em pt-BR

## Módulos

| Módulo | Responsabilidade |
| --- | --- |
| `shared-kernel` | Contratos, identificadores e enums compartilhados |
| `asset-management` | Carteiras, compras, vendas, posições e preço médio |
| `market-tracker` | Provedores de mercado, coleta agendada e cotações |
| `predictive-engine` | Simulações, tendências e projeções |
| `core-api` | Aplicação executável, sessão, segurança e migrações |

`asset-management`, `market-tracker` e `predictive-engine` dependem apenas de `shared-kernel`, nunca uns dos outros. Só o `core-api` reúne todos os módulos.

## Estrutura do código

O pacote raiz é `br.com.chfb.ativo`, onde fica `AtivosApplication`: essa posição faz o scan do Spring alcançar todos os módulos. Dentro de cada módulo, o código é separado por camada. No `core-api`, em `br.com.chfb.ativo.coreapi`:

| Pacote | Conteúdo |
| --- | --- |
| `config` | Classes `@Configuration`: segurança e OpenAPI |
| `controller` | Endpoints REST, sem regra de negócio |
| `dto` | Corpos de requisição e resposta, um `record` por arquivo |
| `entity` | Entidades JPA |
| `exception` | Exceções da aplicação e o `@RestControllerAdvice` |
| `repository` | Repositórios Spring Data |
| `security` | Principal da sessão (`AuthenticatedUser`) |
| `service` | Regras de negócio |

Os demais módulos seguem os mesmos nomes quando ganharem código, por exemplo `br.com.chfb.ativo.assetmanagement.service`.

Mover ou renomear `AuthenticatedUser` invalida as sessões gravadas, porque o nome completo da classe é serializado no banco. As requisições com esses cookies passam a responder `500`, então limpe as sessões junto com a mudança: `DELETE FROM spring_session;`.

## Pré-requisitos

- Docker com Docker Compose
- JDK 17 e Maven 3.9, apenas para rodar ou testar fora de containers

## Ambientes

Os arquivos ficam em `backend/core-api/src/main/resources`:

| Arquivo | Perfil | Uso | Banco |
| --- | --- | --- | --- |
| `application.yml` | Nenhum | Configuração completa, ajustável por variáveis de ambiente | `DB_URL`; padrão `localhost:5432` |
| `application-local.yml` | `local` | Credenciais pessoais na sua máquina; fora do git e opcional | Definido no próprio arquivo |

Variáveis do `application.yml`:

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ativos` | URL JDBC; o Compose aponta para `db:5432` |
| `DB_USERNAME` | `ativos` | Usuário do banco |
| `DB_PASSWORD` | `ativos` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP, só com `java -jar` ou `spring-boot:run`; no Tomcat externo, vale o `server.xml` |
| `SESSION_COOKIE_SECURE` | `false` | Envia o cookie só por HTTPS; use `true` em produção |
| `FORWARD_HEADERS_STRATEGY` | `none` | Com `framework`, confia nos cabeçalhos `X-Forwarded-*`; use só atrás de um proxy |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens autorizadas a chamar a API com credenciais, separadas por vírgula |
| `SWAGGER_ENABLED` | `true` | Publica o Swagger UI e o OpenAPI sem exigir login; o `docker-compose.yml` de produção fixa `false` |
| `MARKET_PROVIDER_CRYPTO` | `binance` | Provedor de cotações de cripto; vazio desliga a coleta do grupo |
| `MARKET_PROVIDER_STOCK` | `brapi` | Provedor de cotações de ações; vazio desliga a coleta do grupo |
| `BRAPI_TOKEN` | Vazio | Token da Brapi; sem ele, só alguns ativos de teste têm cotação (PETR4, VALE3, ITUB4 e MGLU3) |
| `MARKET_COLLECTION_CRON` | `0 0 * * * *` | Agenda da coleta que gera os snapshots patrimoniais |
| `MARKET_COLLECT_ON_STARTUP` | `true` | Atualiza as cotações ao iniciar, sem gerar snapshot |
| `PREDICTIVE_ENABLED` | `true` | Liga o módulo de projeções |
| `APP_ADMIN_EMAIL` | Vazio | Conta promovida a administrador ao iniciar, se existir |

Os padrões são apenas de desenvolvimento: em produção, defina todas elas. O que estiver no `application-local.yml` tem prioridade sobre o `application.yml` quando o perfil `local` está ativo. O Docker Compose lê `APP_ADMIN_EMAIL` e `BRAPI_TOKEN` do arquivo `.env`, que fica fora do git.

Outros ajustes, como o cadastro público e as premissas das projeções, são feitos pelo admin na tela de configurações, sem reiniciar. Veja [`docs/08_etapa_6_administracao_e_permissoes.md`](docs/08_etapa_6_administracao_e_permissoes.md).

## Executar com Docker

```bash
docker compose -f docker-compose.dev.yml up --build -d
docker compose -f docker-compose.dev.yml ps       # db e backend devem ficar "healthy"
```

Para conferir:

```bash
curl http://localhost:8080/actuator/health    # {"status":"UP"}
curl -i http://localhost:8080/api/qualquer    # 401 Unauthorized
docker compose -f docker-compose.dev.yml exec db psql -U ativos -d ativos -c "\dt"
```

## Deploy no Coolify

O `docker-compose.yml` é o de produção e usa as variáveis mágicas do Coolify; o `docker-compose.dev.yml` é o da máquina local. Para os comandos locais continuarem funcionando sem `-f`, adicione `COMPOSE_FILE=docker-compose.dev.yml` ao `.env`.

Para criar o recurso:

1. Em **New Resource**, escolha o repositório pelo GitHub App e o build pack **Docker Compose**, com **Base Directory** `/` e **Docker Compose Location** `/docker-compose.yml`.
2. Confira em **Domains** o domínio gerado para o `nginx`, ou troque-o pelo definitivo. Os outros serviços ficam sem domínio.
3. Em **Environment Variables**, preencha `APP_ADMIN_EMAIL` e `BRAPI_TOKEN`. As demais já têm padrão.
4. Faça o deploy e cadastre a conta com o e-mail de `APP_ADMIN_EMAIL`. Ela é promovida a administrador quando a aplicação inicia, então reinicie o `backend` depois do cadastro.

| Variável | Quem define | Uso |
| --- | --- | --- |
| `SERVICE_URL_NGINX_80` | Coolify | Domínio público da aplicação e porta do Nginx |
| `SERVICE_URL_NGINX` | Coolify | URL base (com `https://`), usada em `CORS_ALLOWED_ORIGINS` |
| `SERVICE_FQDN_NGINX` | Coolify | O mesmo host, sem o protocolo |
| `SERVICE_USER_POSTGRES`, `SERVICE_PASSWORD_POSTGRES` | Coolify | Credenciais do banco, mantidas entre os deploys |

O sistema tem quatro containers, e o `nginx` é a única entrada:

| Caminho | Destino |
| --- | --- |
| `/api/*` | `backend:8080` (Tomcat) |
| `/*` | `frontend:80` (Caddy, que serve os arquivos do Angular) |

O `backend` só fala com o `db`, e nenhum dos dois tem domínio ou porta publicada. Como só `/api/` é encaminhado, `/actuator` e o Swagger não são alcançáveis por fora. O proxy do Coolify continua na frente do `nginx`, cuidando do domínio e do HTTPS.

O Postgres só usa as credenciais ao criar o volume: trocar a senha no painel depois disso não altera a senha do banco, e o `backend` deixa de conectar.

## Executar localmente

Sobe só o banco no Docker e roda a aplicação pelo Maven, que ativa o perfil `local`:

```bash
docker compose -f docker-compose.dev.yml up -d db
cd backend
mvn spring-boot:run
```
or
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

outras formas
```bash
mvn test
```
```bash
mvn compile spring-boot:run
```
```bash
mvn -pl core-api -am test
```

Os comandos Maven rodam dentro de `backend/`, onde está o `pom.xml`. Para rodar pela IDE, execute `AtivosApplication` com o perfil `local` ativo.

### Hot reload

O Spring Boot DevTools reinicia a aplicação quando uma classe compilada muda em qualquer módulo. Ele reage ao `target/classes`, não ao código-fonte, então é preciso recompilar:

- **Terminal:** com a aplicação rodando, execute `mvn compile` em outro terminal, dentro de `backend/`.
- **IntelliJ:** use `Ctrl+F9`. Para compilar ao salvar, ative `Build project automatically` em `Settings > Build, Execution, Deployment > Compiler` e `Allow auto-make to start even if developed application is currently running` em `Settings > Advanced Settings`.

As sessões ficam no banco, então o login continua válido depois do reinício. O DevTools só funciona em execução local: ele não entra no WAR nem na imagem Docker.

**Atenção:** com a aplicação rodando, qualquer `mvn compile` ou `mvn verify` provoca esse reinício, e as migrações novas são aplicadas no banco local na hora. Faça o backup antes de compilar uma mudança de esquema.

## Frontend

Com o backend em `localhost:8080`:

```bash
cd frontend
nvm use          # Node 22
npm ci
npm start        # http://localhost:4200, com /api encaminhado ao backend
npm test -- --watch=false --browsers=ChromeHeadless
```

No `docker compose -f docker-compose.dev.yml up --build`, a aplicação sobe em <http://localhost:4200>: o Nginx encaminha `/api/` ao backend e o restante ao container do frontend. O código fica em `frontend/src/app`: `core` (API, sessão e cálculos), `layout` (estrutura da página) e `pages` (telas).

As tabelas não têm rolagem horizontal: quando falta espaço, cada linha vira um bloco com um campo por linha, e o cabeçalho dá lugar a um seletor "Ordenar por". Para usar em uma tabela nova, veja `src/assets/layout/_stack-table.scss`. O `responsiveLayout="stack"` do PrimeNG está depreciado desde a v20. Os gráficos usam a diretiva `appChartResize`, que os ajusta quando a janela ou o contêiner mudam.

Telas: login, registro, painel para acompanhamento (patrimônio, desempenho, alvos atingidos, perfil da carteira, posições a mercado, evolução patrimonial, alocação e últimas operações), carteira para registrar compras e vendas, acompanhamento de ativos com ou sem posição, extrato com filtro por tipo e lucro realizado, projeções e, para administradores, visão geral, usuários, ativos, configurações e auditoria. O sistema não controla saldo em caixa: registra as compras e vendas feitas pelo usuário.

## Documentação da API

Com a aplicação rodando:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI em JSON: <http://localhost:8080/v3/api-docs>

Para chamar endpoints protegidos pela página, execute `GET /api/auth/csrf` e depois `POST /api/auth/login`. O navegador guarda os cookies e a página envia o token CSRF sozinha, então não é preciso usar o botão `Authorize`.

Com `SWAGGER_ENABLED=false`, essas rotas respondem `404`.

## Autenticação

A sessão usa o cookie `JSESSIONID`, e todo `POST` exige o token CSRF: o valor do cookie `XSRF-TOKEN` vai no header `X-XSRF-TOKEN`. O `HttpClient` do Angular faz isso sozinho, desde que o cookie já exista.

| Endpoint | Sessão | Descrição | Respostas |
| --- | --- | --- | --- |
| `GET /api/auth/csrf` | Não | Entrega o cookie `XSRF-TOKEN`; chame antes do primeiro `POST` e depois do logout | `204` |
| `GET /api/auth/settings` | Não | Informa se o cadastro público está aberto | `200` |
| `POST /api/auth/register` | Não | Cria a conta, sem iniciar sessão. Corpo: `name`, `email`, `password` (mínimo de 8 caracteres), `confirmPassword` e, opcionais, `phone`, `birthDate` e `investorProfile`. Com o cadastro fechado, responde `403` | `201`, `400`, `403`, `409` |
| `POST /api/auth/login` | Não | Inicia a sessão e renova o token CSRF. Corpo: `email`, `password` | `200`, `400`, `401` |
| `GET /api/auth/me` | Sim | Retorna o usuário logado | `200`, `401` |
| `POST /api/auth/logout` | Sim | Encerra a sessão | `204` |
| `GET /api/profile` | Sim | Retorna o perfil completo | `200`, `401` |
| `PUT /api/profile` | Sim | Atualiza `name`, `phone`, `birthDate` e `investorProfile` (`CONSERVATIVE`, `MODERATE` ou `AGGRESSIVE`); o e-mail não muda | `200`, `400`, `401` |
| `POST /api/profile/password` | Sim | Troca a senha: `currentPassword`, `newPassword` e `confirmPassword`. Encerra as outras sessões | `204`, `400`, `401`, `422` |

Para testar pelo terminal:

```bash
API=http://localhost:8080/api/auth
curl -c cookies.txt $API/csrf
TOKEN=$(awk '$6 == "XSRF-TOKEN" {print $7}' cookies.txt)
HEADERS=(-H "Content-Type: application/json" -H "X-XSRF-TOKEN: $TOKEN")

curl -b cookies.txt -c cookies.txt "${HEADERS[@]}" $API/register \
  -d '{"name":"Ana","email":"ana@example.com","password":"senha-forte-123","confirmPassword":"senha-forte-123"}'
curl -b cookies.txt -c cookies.txt "${HEADERS[@]}" $API/login \
  -d '{"email":"ana@example.com","password":"senha-forte-123"}'
curl -b cookies.txt $API/me
```

## Cotações e dashboard

A coleta de hora cheia consulta só os símbolos com posição aberta, grava a última cotação em `market_quotes`, acrescenta o preço em `market_quote_history` e gera um ponto em `portfolio_snapshots` para cada carteira. Quando um símbolo ainda não tem histórico, a coleta também busca no provedor os fechamentos diários do último ano, para os gráficos de mercado já nascerem com dados; ações fora da lista de teste da Brapi precisam de `BRAPI_TOKEN`, e um plano gratuito pode limitar a carga a três meses. Se um provedor falhar, o símbolo mantém a última cotação conhecida e o log registra um aviso. Cripto é cotada em BRL pelo par da Binance, como `BTCBRL`.

| Endpoint | Descrição |
| --- | --- |
| `GET /api/dashboard/overview` | Valor investido, valor a mercado e retorno |
| `GET /api/dashboard/positions` | Posições com preço médio, cotação e lucro ou prejuízo; sem cotação, valem pelo preço médio |
| `GET /api/dashboard/history?days=30` | Pontos do histórico patrimonial, de 1 a 365 dias |
| `GET /api/dashboard/market?days=30` | Por posição aberta: variação em 24 horas e no período, mínima, máxima e preços, de 1 a 365 dias |
| `GET /api/dashboard/sentiment?days=30` | Índice de ganância e medo da carteira: leitura atual, componentes e pontos diários, de 1 a 365 dias |

Para adicionar um provedor, implemente `CryptoMarketProvider` ou `StockMarketProvider` com `@ConditionalOnProperty` e aponte `market.provider.*` para ele.

## Sincronização das cotações

A coleta é única para todo o sistema: cada símbolo é consultado uma vez, não importa quantos usuários o tenham, e o preço vale para todos. Se dois usuários têm BTC, a coleta faz uma só consulta de BTC.

| Origem | Quando roda | Abrangência |
| --- | --- | --- |
| Agendada | Em `MARKET_COLLECTION_CRON`; só esta gera snapshots | Todos os símbolos em carteira ou acompanhados |
| Início da aplicação | Com `MARKET_COLLECT_ON_STARTUP=true` | Todos os símbolos em carteira ou acompanhados |
| Admin | "Coletar agora", em **Administração > Sincronização**, respeitando o intervalo mínimo | Todos os símbolos em carteira ou acompanhados |
| Símbolo novo | Logo após uma compra ou um novo acompanhamento, sem atrasar a resposta | Só o que falta daquele símbolo; nenhuma chamada externa se ele já estiver coberto |

Os usuários comuns aguardam a coleta automática, então os preços podem ficar até 1 hora defasados com o agendamento padrão. Com a coleta pausada nas configurações, um símbolo novo também espera a retomada.

A carga de fechamentos diários roda quando o símbolo nunca teve preço com mais de 1 dia ou está há mais de 2 dias sem preços, como um ativo que voltou a ser coletado. Assim o histórico não fica com um buraco. Ela é refeita a cada coleta enquanto falhar.

| Endpoint | Acesso | Descrição |
| --- | --- | --- |
| `GET /api/market/sync-status` | Usuário logado | Última e próxima sincronização, para o indicador da topbar. Lê do banco, sem chamar os provedores |
| `GET /api/admin/sync/runs` | Admin | Execuções paginadas; filtros `status`, `origin` e `symbol` |
| `GET /api/admin/sync/runs/{id}/items` | Admin | Resultado de cada símbolo, com o motivo das falhas |
| `POST /api/admin/quotes/collect` | Admin | Coleta agora; com `refreshed: false`, a última foi há menos que o intervalo mínimo |

Cada execução termina como `SUCCESS`, `PARTIAL` (algo falhou), `FAILED` (nenhum preço novo) ou `INTERRUPTED` (a aplicação parou durante a coleta). A busca de um símbolo novo não conta como sincronização geral no indicador. Os registros são apagados depois de 90 dias, prazo ajustável nas configurações.

## Acompanhamento

A tela **Acompanhamento** lista ativos que o usuário quer observar, tendo ou não uma posição. Nada aqui altera a carteira, o patrimônio ou a rentabilidade. O plano é privado; a cotação vem da coleta centralizada, então vários usuários acompanhando o mesmo ativo geram uma só consulta. Um ativo sai da coleta quando ninguém mais o tem nem o acompanha.

| Endpoint | Descrição | Respostas |
| --- | --- | --- |
| `GET /api/watchlist?days=30` | Ativos acompanhados, com plano, dados de mercado, posição na carteira e sinal técnico | `200` |
| `POST /api/watchlist` | Passa a acompanhar. Corpo: `symbol`, `assetType` e, opcionais, `name`, `buyBelow`, `sellAbove`, `stopLoss`, `note` e `acceptUnverified` | `201`, `400`, `409`, `422` |
| `PUT /api/watchlist/{id}` | Substitui o plano: `buyBelow`, `sellAbove`, `stopLoss` e `note` | `200`, `400`, `404`, `422` |
| `DELETE /api/watchlist/{id}` | Deixa de acompanhar e apaga o plano | `204`, `404` |

- **Alvos:** "comprar abaixo de" é atingido com a cotação no preço ou abaixo dele; "vender acima de", no preço ou acima; o stop, no preço ou abaixo. Venda e stop só geram aviso para quem tem o ativo. Os alvos atingidos aparecem nas telas de Acompanhamento e Painel, e o plano também na Carteira, onde pode ser definido.
- **Regras:** o alvo de venda precisa ser maior que o preço de compra e que o stop. O mesmo ativo não pode ser adicionado duas vezes. O limite é de 100 ativos por usuário, ajustável pelo admin; ele só protege a cota das APIs contra abuso, porque o custo depende dos símbolos distintos, não dos usuários.
- **Catálogo:** um ativo acompanhado não pode ser removido pelo admin. Na primeira vez em que o sistema vê um símbolo, ele é conferido no provedor, como na compra.

## Sinal técnico

Cada ativo do Acompanhamento mostra uma leitura técnica, calculada por símbolo e igual para todos os usuários. O cálculo usa o histórico gravado e nunca chama os provedores. Ele descreve o histórico de preços: não prevê o futuro, não diz para comprar ou vender e não é recomendação de investimento.

| Indicador | Cálculo | Leitura |
| --- | --- | --- |
| RSI de 14 dias | Método de Wilder sobre fechamentos diários | Até 30: sobrevendido; a partir de 70: sobrecomprado; entre eles: neutro |
| Médias de 20 e 50 dias | Média simples dos fechamentos | Média de 20 acima da de 50: tendência de alta; abaixo: de baixa |

A tela sempre mostra os dois juntos, porque um RSI baixo em tendência de baixa pode ser só um ativo que continua caindo. Os limites do RSI são ajustados pelo admin nas configurações e valem na hora; os períodos são fixos.

- **Dados insuficientes:** a leitura exige 50 fechamentos e preços dos últimos 5 dias. Ações fora da lista de teste da Brapi precisam de `BRAPI_TOKEN` para ter histórico.
- **Dias sem pregão:** a coleta grava preço todos os dias. Fechamentos repetidos ficam de fora do cálculo para fins de semana e feriados não distorcerem os indicadores.
- **Limites dos dados:** só há preço de fechamento, sem volume nem máxima e mínima do dia. O último ponto é a cotação mais recente, então a leitura pode mudar durante o pregão.
- **Módulo desligado:** com `PREDICTIVE_ENABLED=false`, `signal` vem nulo e a coluna não aparece.

## Perfil da carteira

O Painel mostra um gráfico de radar com seis notas de 0 a 100, sempre com 100 no lado favorável. Elas descrevem a carteira e não são recomendação de investimento. Um aspecto sem dados sai do desenho, em vez de parecer nota zero. Cada vértice tem a cor da faixa da nota: vermelho de 0 a 33 (baixa), azul de 34 a 66 (média) e verde de 67 a 100 (alta).

| Aspecto | Como é medido | Escala |
| --- | --- | --- |
| Diversificação | Quanto da concentração a divisão elimina: (1 − soma dos pesos ao quadrado) ÷ 0,9 | 1 ativo é 0; 2 de mesmo peso é 56; 5 é 89; 10 é 100 |
| Rentabilidade | Lucro ou prejuízo sobre o custo, das posições com cotação | -50% é 0; zero é 50; +50% é 100 |
| Momento | Variação de preços no período escolhido, ponderada pelo valor, só com ativos que têm preços de quase todo o período | Zero é 50; os extremos são ±20% em 30 dias e crescem com a raiz do tempo: ±10% em 7 dias, ±35% em 90 e ±70% em 1 ano |
| Estabilidade | Volatilidade anualizada no período, média ponderada pelo valor de cada ativo | 0% é 100; 100% ao ano ou mais é 0 |
| Posições no lucro | Parte das posições com cotação acima do custo | Percentual direto |
| Plano de saída | Parte das posições com alvo de venda ou stop no Acompanhamento | Percentual direto |

Um ativo tem histórico curto quando falta mais que 10% do período ou 5 dias, o que for maior. Ele fica fora do momento, e o momento fica sem nota se os ativos medidos somarem menos de 50% do valor da carteira. Isso evita somar, em "1 ano", um ano de uma moeda com os 3 meses de histórico de uma ação. Nas demais telas de mercado, a variação continua aparecendo, com o período real ao lado, como "em 88 dias".

A diversificação olha só os pesos: 10 criptomoedas de mesmo peso também valem 100, embora costumem cair juntas. A estabilidade é uma aproximação: a média ignora a correlação entre os ativos e, em 7 dias, usa poucos pontos, então a nota oscila mais.

## Ganância e medo

O Painel mostra um índice de 0 (medo extremo) a 100 (ganância extrema), com um medidor da leitura atual e a curva do período, com uma cor por faixa: vermelho escuro, laranja, azul, verde-claro e verde escuro. Ele usa só os fechamentos gravados e nunca chama os provedores. Sem volume, opções ou redes sociais, descreve o comportamento dos preços, não o humor do mercado; não prevê o futuro e não é recomendação de investimento.

| Componente (25% cada) | Cálculo | Nota |
| --- | --- | --- |
| Força das altas | RSI de 14 dias, de Wilder | Valor direto |
| Distância da média | Distância até a média de 50 fechamentos ÷ (volatilidade diária de 50 × 5) | -2 é 0; +2 é 100 |
| Posição na faixa | Onde o preço está entre a mínima e a máxima de 50 fechamentos | Percentual direto |
| Calmaria | Volatilidade de 20 fechamentos ÷ volatilidade de 50 | O dobro é 0; igual é 50; a metade é 100 |

| Faixa | Leitura |
| --- | --- |
| 0–24 | Medo extremo |
| 25–44 | Medo |
| 45–55 | Neutro |
| 56–75 | Ganância |
| 76–100 | Ganância extrema |

- **Quando é calculado:** por símbolo, igual para todos, ao fim de cada coleta agendada, em `market_sentiment_daily`. Refaz o último dia calculado e os seguintes. A tabela é derivada: pode ser apagada e é refeita na coleta seguinte. A coleta pelo admin, a do início da aplicação e a de um símbolo novo não atualizam o índice, então um símbolo novo entra na próxima coleta agendada.
- **Por que não na compra e venda:** a operação não muda preços, a data pode ser retroativa e um símbolo novo ainda não tem histórico. Os pesos são aplicados só na leitura, com o valor atual de cada posição, então compras, vendas e estornos valem na hora, sem nada para corrigir, e a curva inteira é redesenhada: ela mostra os ativos de hoje no passado.
- **Dados insuficientes:** a nota de um símbolo exige 51 fechamentos diferentes em 200 dias. Quem não tem nota sai da média, e a resposta informa a parte coberta em `coverage`. Dias com menos de 50% de cobertura ficam de fora. Ações fora da lista de teste da Brapi precisam de `BRAPI_TOKEN`.
- **Dias sem pregão:** a última nota de um ativo é repetida por até 5 dias, para a curva não saltar nos fins de semana. A linha nunca passa da última nota real; com ela mais antiga que isso, `stale` vem verdadeiro e o card avisa.
- **O dia** segue o fuso de São Paulo.
- **Limite conhecido:** força das altas, distância da média e posição na faixa se movem juntas (correlação perto de 0,9 em BTC, ETH, PETR4 e VALE3), então o índice pesa mais a tendência recente. Os pesos e limites são um ponto de partida.
- **Módulo desligado:** com `PREDICTIVE_ENABLED=false`, o endpoint deixa de existir e o card não aparece.

## Busca de ativos

`GET /api/assets?query=pe` procura no catálogo de ativos já cadastrados, compartilhado entre os usuários, pelo início do símbolo ou por parte do nome, e devolve a última cotação gravada em `price`, sem chamar os provedores. A tela de compra usa essa busca para sugerir o símbolo e preencher o tipo e o preço; símbolos novos são cadastrados na primeira compra.

Nas telas de compra e venda, o preço unitário já vem com a última cotação do sistema, que pode ser ajustada. O que o usuário digitou nunca é substituído, e numa operação com data passada a tela avisa que o valor é a cotação de agora.

`GET /api/assets/lookup?query=petr&type=STOCK` procura no provedor de cotações, inclusive ativos ainda não cadastrados, e devolve nome e cotação atual. A mesma pesquisa, de qualquer usuário, é respondida do cache por 5 minutos. Em ações, usa a listagem da Brapi. Em cripto, só encontra pares negociados na Binance na moeda de cotação (BRL), os únicos que o sistema consegue cotar. Com `available: false`, o provedor não respondeu, o que não significa que o ativo não existe.

No primeiro cadastro, o símbolo é conferido no provedor de cotações. Se ele não existir, ou só existir no outro tipo, a compra responde `422` com `code: SYMBOL_NOT_FOUND` e, se houver, `suggestedType`. Com `acceptUnverified: true`, o ativo é cadastrado como não verificado. Sem resposta do provedor, como ações fora da lista de teste da Brapi sem `BRAPI_TOKEN`, o cadastro é aceito como não verificado.

## Preço médio

O preço médio inclui as taxas de compra, então o lucro das vendas é calculado sobre o custo real. Preços e preço médio têm até oito casas decimais; taxas e totais, quatro.

## Data da operação

`POST /api/transactions/buy` e `/sell` aceitam `executedAt` (ISO 8601), para lançar operações passadas; sem ele, vale o momento atual, e datas futuras são recusadas. Toda operação refaz a posição a partir do histórico, em ordem de data, então um lançamento retroativo recalcula o preço médio e o lucro das vendas seguintes, e é recusado (`422`) se deixar alguma venda sem quantidade. A coluna `recorded_at` guarda quando o lançamento foi feito.

## Extrato

`GET /api/transactions` devolve as operações da mais recente à mais antiga, paginadas como o `simplePaginate` do Laravel: não há `COUNT`, nem `total` e `last_page`. A consulta busca uma linha além de `per_page` para saber se existe outra página.

```json
{
  "current_page": 2,
  "data": [],
  "first_page_url": "/api/transactions?type=BUY&per_page=20&page=1",
  "from": 21,
  "next_page_url": "/api/transactions?type=BUY&per_page=20&page=3",
  "path": "/api/transactions",
  "per_page": 20,
  "prev_page_url": "/api/transactions?type=BUY&per_page=20&page=1",
  "to": 40
}
```

Os links são relativos, para valerem atrás de proxy, e mantêm os filtros. `from` e `to` ficam nulos numa página sem itens.

| Parâmetro | Filtra por |
| --- | --- |
| `page`, `per_page` | Página, a partir de 1 (padrão `1`), e itens por página, até 200 (padrão `50`) |
| `asset` | Parte do símbolo ou do nome, sem diferenciar maiúsculas |
| `type` | `BUY` ou `SELL` |
| `assetType` | `STOCK` ou `CRYPTO` |
| `status` | `ACTIVE` (válidas) ou `REVERSED` (estornadas) |
| `from`, `to` | Data da operação, em ISO 8601, inclusive os extremos |
| `minTotal`, `maxTotal` | Valor total da operação, inclusive os extremos |

Um intervalo invertido responde `400`. A tela de extrato abre com os últimos 90 dias: limpar as duas datas mostra todo o histórico.

## Estorno

`POST /api/transactions/{id}/reverse` corrige uma compra ou venda registrada por engano. Nada é apagado: a operação continua no extrato como estornada, e a posição é refeita a partir das demais, em ordem, recalculando o preço médio e o lucro realizado das vendas seguintes.

O estorno é recusado (`422`) se uma venda posterior ficaria sem quantidade: nesse caso, estorne a venda antes.

Os snapshots antigos não são reescritos, mas `GET /api/dashboard/history` não conta o que foi estornado: os pontos gravados entre o lançamento e o estorno de uma operação são recalculados na leitura, só com as operações válidas lançadas até ali e o preço daquele instante (ou o custo, se não havia preço). Um ponto sem nenhuma posição restante sai do histórico. Assim, a evolução patrimonial, o retorno e as projeções ignoram os estornos, inclusive os antigos. Lançamentos retroativos continuam sem alterar os pontos anteriores a eles.

## Projeções

`POST /api/predictions/simulate` recebe `months` (6, 12, 24 ou 60), `monthlyContribution` e `scenario` (`OPTIMISTIC`, `REALISTIC` ou `PESSIMISTIC`) e devolve a curva com intervalo de confiança de 80%, as premissas e os alertas de risco.

- **Retorno e volatilidade:** medidos no histórico de cotações dos ativos de hoje, ponderados pelo valor atual, então compras e vendas não afetam o resultado. O retorno é a tendência de uma regressão linear sobre o logaritmo do índice da carteira, limitada a uma faixa de -50% a +100%. Ativos com menos de 60 dias de cotações usam os valores de referência, que são premissas configuradas, não dados de mercado. A resposta informa a origem em `assumptions.source` (`MARKET`, `BLENDED` ou `REFERENCE`) e a parte medida em `assumptions.coverage`.
- **Cenários:** o otimista soma 5 pontos percentuais ao retorno anual; o pessimista subtrai 5 e aplica uma queda imediata de 40% em cripto (ou de 20% em ações, se não houver cripto). A tendência olha para trás: depois de um ano de queda, o cenário realista também aponta queda.
- **Alertas:** mais de 70% em cripto ou mais de 50% em um único ativo.

Os padrões ficam em `predictive.*` no `application.yml`, e o admin pode alterá-los na tela de configurações, exceto o nível de confiança. Com `PREDICTIVE_ENABLED=false`, o endpoint deixa de existir e o restante do sistema não muda.

## Administração

`/api/admin/**` exige o papel `ADMIN`. O primeiro administrador é definido por `APP_ADMIN_EMAIL`: crie a conta pela tela de registro e reinicie. O admin vê totais, nunca as carteiras de outros usuários. Regras e telas estão em [`docs/08_etapa_6_administracao_e_permissoes.md`](docs/08_etapa_6_administracao_e_permissoes.md).

| Endpoint | Descrição |
| --- | --- |
| `GET /api/admin/overview` | Totais e saúde das cotações |
| `POST /api/admin/quotes/collect` | Coleta as cotações agora, respeitando o intervalo mínimo |
| `POST /api/admin/positions/rebuild` | Refaz todas as posições a partir das operações |
| `GET`, `POST /api/admin/users` | Lista e cria contas |
| `PUT /api/admin/users/{id}` | Edita a conta; mudar e-mail ou papel encerra as sessões dela |
| `PUT /api/admin/users/{id}/active` | Ativa ou desativa a conta |
| `POST /api/admin/users/{id}/password` | Define uma nova senha e encerra as sessões |
| `POST /api/admin/users/{id}/sessions/end` | Encerra as sessões da conta |
| `GET /api/admin/assets` | Lista o catálogo de ativos |
| `PUT`, `DELETE /api/admin/assets/{id}` | Corrige nome e tipo; remove um ativo sem operações |
| `POST /api/admin/assets/{id}/verify` | Confere o ativo no provedor de cotações |
| `GET`, `PUT /api/admin/settings` | Lê e altera as configurações gerais |
| `DELETE /api/admin/settings/{key}` | Volta uma configuração ao valor padrão |
| `GET /api/admin/audit` | Lista as ações administrativas |

**Ao atualizar de uma versão anterior:** execute "Recalcular posições" uma vez na visão geral. A taxa de compra passou a fazer parte do custo, e preço médio e lucro das vendas antigas só mudam com esse recálculo.

## Deploy em Tomcat

O build (`mvn clean package`) gera `backend/core-api/target/ativos-backend.war`. Sem o `clean`, classes de arquivos já removidos podem continuar no WAR e impedir a aplicação de iniciar. Use o Tomcat 10.1: versões anteriores à 10 não são compatíveis com Jakarta EE.

1. Defina as variáveis de produção da seção [Ambientes](#ambientes) no processo do Tomcat, por exemplo em `bin/setenv.sh`.
2. Copie o arquivo para `webapps/ROOT.war`, para publicar a aplicação na raiz. Com outro nome, o nome do arquivo vira prefixo das URLs.

A imagem Docker do backend já faz isso. O mesmo arquivo também roda sem Tomcat instalado, com `java -jar ativos-backend.war`.

## Testes

```bash
cd backend
mvn verify
```

- `SecurityConfigTest` não precisa de Docker.
- `AtivosApplicationIT`, `AuthFlowIT`, `WalletFlowIT`, `OpenApiIT` e `OpenApiDisabledIT` sobem um PostgreSQL real com Testcontainers. **Sem Docker disponível, esses testes são ignorados sem falhar o build.** Confira no resultado se eles foram executados.

## Migrações

As migrações ficam em `backend/core-api/src/main/resources/db/migration`, em uma única sequência (`V2__descricao.sql`, `V3__...`), porque o Flyway exige versões únicas no classpath. Elas rodam sozinhas ao iniciar a aplicação.

O Flyway não gerencia as tabelas `spring_session*`: o próprio Spring Session as cria.

## Status

| Etapa | Situação |
| --- | --- |
| 1. Arquitetura base e autenticação | Concluída |
| 2. Gestão de ativos | Concluída |
| 3. Cotações e provedores | Concluída |
| 4. Dashboard | Concluída |
| 5. Predição e análise | Concluída |
| 6. Administração e permissões | Concluída |

## Comandos úteis

```bash
docker compose -f docker-compose.dev.yml logs -f backend    # acompanha os logs
docker compose -f docker-compose.dev.yml down               # para os containers e mantém os dados
docker compose -f docker-compose.dev.yml down -v            # para os containers e APAGA os dados do banco
```
