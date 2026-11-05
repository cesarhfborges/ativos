# Sistema de Gestão de Ativos e Investimentos

Sistema multiusuário para acompanhar carteiras de ações e criptomoedas: patrimônio, rentabilidade, histórico e projeções. O escopo completo está em [`docs/`](docs/).

O repositório contém autenticação, registro de compras e vendas, coleta de cotações, dashboard e o frontend que usa esses recursos. Projeções (Etapa 5) ainda não existem.

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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens autorizadas a chamar a API com credenciais, separadas por vírgula |
| `SWAGGER_ENABLED` | `true` | Publica o Swagger UI e o OpenAPI sem exigir login; use `false` em produção |
| `MARKET_PROVIDER_CRYPTO` | `binance` | Provedor de cotações de cripto; vazio desliga a coleta do grupo |
| `MARKET_PROVIDER_STOCK` | `brapi` | Provedor de cotações de ações; vazio desliga a coleta do grupo |
| `BRAPI_TOKEN` | Vazio | Token da Brapi; sem ele, só alguns ativos de teste têm cotação (PETR4, VALE3, ITUB4 e MGLU3) |
| `MARKET_COLLECTION_CRON` | `0 0 * * * *` | Agenda da coleta que gera os snapshots patrimoniais |
| `MARKET_COLLECT_ON_STARTUP` | `true` | Atualiza as cotações ao iniciar, sem gerar snapshot |

Os padrões são apenas de desenvolvimento: em produção, defina todas elas. O que estiver no `application-local.yml` tem prioridade sobre `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` quando o perfil `local` está ativo.

## Executar com Docker

```bash
docker compose up --build -d
docker compose ps       # db e backend devem ficar "healthy"
```

Para conferir:

```bash
curl http://localhost:8080/actuator/health    # {"status":"UP"}
curl -i http://localhost:8080/api/qualquer    # 401 Unauthorized
docker compose exec db psql -U ativos -d ativos -c "\dt"
```

## Executar localmente

Sobe só o banco no Docker e roda a aplicação pelo Maven, que ativa o perfil `local`:

```bash
docker compose up -d db
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

## Frontend

Com o backend em `localhost:8080`:

```bash
cd frontend
nvm use          # Node 22
npm ci
npm start        # http://localhost:4200, com /api encaminhado ao backend
npm test -- --watch=false --browsers=ChromeHeadless
```

No `docker compose up --build`, o frontend sobe em <http://localhost:4200>, servido pelo Nginx, que encaminha `/api/` ao backend. O código fica em `frontend/src/app`: `core` (API, sessão e cálculos), `layout` (estrutura da página) e `pages` (telas).

Telas: login, registro, painel (patrimônio, desempenho, posições a mercado, evolução patrimonial, alocação e últimas operações, com registro de compra e venda) e extrato com filtro por tipo. O sistema não controla saldo em caixa: registra as compras e vendas feitas pelo usuário.

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
| `POST /api/auth/register` | Não | Cria a conta, sem iniciar sessão. Corpo: `name`, `email`, `password` (mínimo de 8 caracteres) | `201`, `400`, `409` |
| `POST /api/auth/login` | Não | Inicia a sessão e renova o token CSRF. Corpo: `email`, `password` | `200`, `400`, `401` |
| `GET /api/auth/me` | Sim | Retorna o usuário logado | `200`, `401` |
| `POST /api/auth/logout` | Sim | Encerra a sessão | `204` |

Para testar pelo terminal:

```bash
API=http://localhost:8080/api/auth
curl -c cookies.txt $API/csrf
TOKEN=$(awk '$6 == "XSRF-TOKEN" {print $7}' cookies.txt)
HEADERS=(-H "Content-Type: application/json" -H "X-XSRF-TOKEN: $TOKEN")

curl -b cookies.txt -c cookies.txt "${HEADERS[@]}" $API/register \
  -d '{"name":"Ana","email":"ana@example.com","password":"senha-forte-123"}'
curl -b cookies.txt -c cookies.txt "${HEADERS[@]}" $API/login \
  -d '{"email":"ana@example.com","password":"senha-forte-123"}'
curl -b cookies.txt $API/me
```

## Cotações e dashboard

A coleta de hora cheia consulta só os símbolos com posição aberta, grava a última cotação em `market_quotes` e gera um ponto em `portfolio_snapshots` para cada carteira. Se um provedor falhar, o símbolo mantém a última cotação conhecida e o log registra um aviso. Cripto é cotada em BRL pelo par da Binance, como `BTCBRL`.

| Endpoint | Descrição |
| --- | --- |
| `GET /api/dashboard/overview` | Valor investido, valor a mercado e retorno |
| `GET /api/dashboard/positions` | Posições com preço médio, cotação e lucro ou prejuízo; sem cotação, valem pelo preço médio |
| `GET /api/dashboard/history?days=30` | Pontos do histórico patrimonial, de 1 a 365 dias |
| `POST /api/dashboard/refresh-quotes` | Atualiza as cotações agora, uma vez por minuto no máximo, sem gerar snapshot |

Para adicionar um provedor, implemente `CryptoMarketProvider` ou `StockMarketProvider` com `@ConditionalOnProperty` e aponte `market.provider.*` para ele.

## Deploy em Tomcat

O build gera `backend/core-api/target/ativos-backend.war`. Use o Tomcat 10.1: versões anteriores à 10 não são compatíveis com Jakarta EE.

1. Defina as variáveis de produção da seção [Ambientes](#ambientes) no processo do Tomcat, por exemplo em `bin/setenv.sh`.
2. Copie o arquivo para `webapps/ROOT.war`, para publicar a aplicação na raiz. Com outro nome, o nome do arquivo vira prefixo das URLs.

A imagem Docker do backend já faz isso. O mesmo arquivo também roda sem Tomcat instalado, com `java -jar ativos-backend.war`.

## Testes

```bash
cd backend
mvn verify
```

- `SecurityConfigTest` não precisa de Docker.
- `AtivosApplicationIT`, `AuthFlowIT`, `OpenApiIT` e `OpenApiDisabledIT` sobem um PostgreSQL real com Testcontainers. **Sem Docker disponível, esses testes são ignorados sem falhar o build.** Confira no resultado se eles foram executados.

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
| 5. Predição e análise | Não iniciada |

## Comandos úteis

```bash
docker compose logs -f backend    # acompanha os logs
docker compose down               # para os containers e mantém os dados
docker compose down -v            # para os containers e APAGA os dados do banco
```
