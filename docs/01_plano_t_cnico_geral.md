# Plano Técnico Geral da Aplicação

## 1. Estrutura de Diretórios e Docker

O repositório adota a divisão em pastas colaterais na raiz (`backend` e `frontend`), com orquestração via Docker e Docker Compose:

```
investment-system/
├── docker-compose.yml       # Orquestração: PostgreSQL, Backend e Frontend
├── backend/                 # Aplicação Spring Boot (Monólito Modular Maven)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── shared-kernel/       # Contratos comuns, DTOs, Enums globais
│   ├── core-api/            # Auth, Spring Session, Web Security, Controllers de entrada
│   ├── asset-management/    # Carteiras, Compras, Vendas, Posições e Preço Médio
│   ├── market-tracker/      # Provedores de mercado, Job agendado, Cotações
│   └── predictive-engine/   # Motor preditivo, Análises e Projeções de Cenários
└── frontend/                # Aplicação Angular (SPA)
    ├── Dockerfile
    ├── nginx.conf           # Configuração de proxy reverso e roteamento HTML5
    ├── package.json
    ├── angular.json
    └── src/
```

### Orquestração Docker Compose
* **`postgres`:** Imagem oficial PostgreSQL 16+, volume persistente local e healthcheck ativo.
* **`backend`:** Build multi-stage (Maven build -> OpenJDK JRE), conectando ao Postgres via rede interna Docker.
* **`frontend`:** Build multi-stage (Node build -> Nginx Alpine), expondo a porta HTTP pública e direcionando requisições `/api/` para o container do backend.

---

## 2. Tecnologias Padronizadas

* **Backend:** Java 17/21 com Spring Boot 3.x (Spring Web, Spring Security, Spring Session JDBC, Spring Data JPA, Flyway, Spring Validation).
* **Frontend:** Angular 17+ (Standalone Components, Signals/RxJS, HttpClient com interceptors para credenciais).
* **Database:** PostgreSQL.
* **Containerização:** Docker e Docker Compose.

---

## 3. Modelo de Dados Unificado (PostgreSQL)

O banco armazena as tabelas de sessão, segurança, operações financeiras e cotações:

* `users`: `id (UUID)`, `name`, `email`, `password_hash`, `created_at`, `phone`, `birth_date`, `investor_profile`, `role (USER | ADMIN)`, `active`, `last_login_at`
* `spring_session` & `spring_session_attributes`: Tabelas nativas do Spring Session JDBC.
* `wallets`: `id (UUID)`, `user_id (FK)`, `name`
* `assets`: `id (UUID)`, `symbol (VARCHAR)`, `name`, `asset_type (STOCK | CRYPTO)`, `verification_status (VERIFIED | UNVERIFIED)`
* `wallet_positions`: `id (UUID)`, `wallet_id (FK)`, `asset_id (FK)`, `quantity (NUMERIC 24, 8)`, `average_price (NUMERIC 24, 8)`
* `transactions`: `id (UUID)`, `wallet_id (FK)`, `asset_id (FK)`, `type (BUY | SELL)`, `quantity (NUMERIC 24, 8)`, `unit_price (NUMERIC 24, 8)`, `fee (NUMERIC 18, 4)`, `total_amount (NUMERIC 18, 4)`, `realized_profit (NUMERIC 18, 4)`, `timestamp`, `recorded_at`, `reversed_at`
* `market_quotes`: `id (UUID)`, `symbol`, `price (NUMERIC 24, 8)`, `last_updated_at`. Só a última cotação de cada símbolo.
* `market_quote_history`: `symbol`, `quoted_at`, `price (NUMERIC 24, 8)`. Fechamentos diários da carga inicial e o preço de cada coleta.
* `portfolio_snapshots`: `id (UUID)`, `wallet_id (FK)`, `invested_balance`, `total_balance`, `snapshot_time`
* `app_settings`: `key`, `value`, `updated_at`, `updated_by`. Só as configurações alteradas pelo admin.
* `admin_audit_log`: `id (UUID)`, `actor_email`, `action`, `target`, `details`, `created_at`

Preços e preço médio têm oito casas, porque criptos muito baratas não cabem em quatro; taxas e totais têm quatro.

---

## 4. Regra Transacional: Registro de Compras e Vendas

O sistema não controla saldo em caixa: registra as operações que o usuário fez fora dele.
1. **Compra:** registra a transação `BUY` e cria/atualiza a posição em `wallet_positions`, recalculando o preço médio com a taxa no custo. No primeiro cadastro de um símbolo, o provedor de cotações é consultado antes da transação (veja a Etapa 2).
2. **Venda:** exige custódia suficiente, registra a transação `SELL` e reduz a posição.
3. Ambas rodam em uma única transação atômica (`@Transactional`), e o histórico fica 100% auditável.
4. **Estorno:** corrige um registro errado sem apagá-lo. A operação recebe `reversed_at`, sai dos cálculos e a posição é refeita a partir das operações válidas, em ordem, recalculando o preço médio e o lucro realizado das vendas seguintes. É recusado se uma venda posterior ficaria sem custódia.

---

## 5. Padrão de Integração de Cotações (Strategy Pattern)

O módulo `market-tracker` isola chamadas externas via Strategy:

```java
public interface CryptoMarketProvider {
    Map<String, BigDecimal> getPrices(Set<String> activeSymbols);
}
```

* Provedor ativo selecionado via `@ConditionalOnProperty(name = "market.provider.crypto", havingValue = "binance")`.
* Apenas ativos com posições ativas (`quantity > 0`) entram no lote de consulta externa.

---

## 6. Isolamento e Segurança de Sessão
* **Sessão:** Stateful com cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`).
* **Isolamento de Usuário:** Toda busca de carteiras ou posições filtra internamente pelo `userId` recuperado do `SecurityContextHolder`, impedindo manipulações de requisição.
* **Angular:** Configurado com `withCredentials: true` no cliente HTTP.