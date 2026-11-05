# Etapa 1: Arquitetura Base, Infraestrutura e Autenticação

## Objetivo
Estruturar o repositório nas pastas colaterais `backend/` (Spring Boot modular) e `frontend/` (Angular), configurar o ambiente Docker completo (PostgreSQL + containers dos apps) e implementar a autenticação multiusuário baseada em sessão.

---

## Tarefas Técnicas

### 1. Estrutura de Pastas e Orquestração Docker
1. Criar a raiz do projeto com as pastas:
   * `/backend`
   * `/frontend`
   * `docker-compose.yml`
2. Configurar o `docker-compose.yml` com os serviços:
   * `db`: PostgreSQL com volume montado para persistência de dados.
   * `backend`: Dockerfile multi-stage (Maven build -> Eclipse Temurin JRE).
   * `frontend`: Dockerfile multi-stage (Node.js build -> Nginx para servir estáticos e rotear `/api`).
3. Definir rede bridge interna compartilhada para comunicação entre backend e PostgreSQL.

### 2. Backend (Spring Boot 3.x)
1. Iniciar na pasta `backend/` o projeto multi-módulo Maven:
   * `shared-kernel`
   * `core-api`
   * `asset-management`
   * `market-tracker`
   * `predictive-engine`
2. Configurar PostgreSQL com migrações gerenciadas pelo Flyway:
   * Criar tabela `users`.
   * Habilitar criação automática das tabelas de sessão do `spring-session-jdbc`.
3. Configurar Spring Security no `core-api`:
   * Autenticação via endpoint `/api/auth/login` gerando cookie de sessão `JSESSIONID` (`HttpOnly`, `SameSite=Lax`).
   * Configuração de CORS para permitir requisições com credenciais (`allowCredentials = true`).
   * Proteção de rotas `/api/**` exigindo autenticação ativa (exceto endpoints públicos de autenticação e registro).

### 3. Frontend (Angular 17+)
1. Iniciar a aplicação Angular dentro da pasta `frontend/`.
2. Configurar o `proxy.conf.json` para ambiente de desenvolvimento local apontando `/api` para o backend (`http://localhost:8080`).
3. Criar serviço de autenticação (`AuthService`) com métodos `login()`, `logout()` e `getCurrentUser()`.
4. Implementar `HttpInterceptor` que anexa `{ withCredentials: true }` em todas as requisições HTTP e redireciona em caso de erro `401`.
5. Criar Guardas de Rota (`AuthGuard`) para impedir acesso não autenticado a rotas protegidas.
6. Desenvolver telas de Login e Registro de Usuário.

---

## Critérios de Aceite
* [ ] Executar `docker compose up --build` sobe os serviços de PostgreSQL, backend e frontend sem erros.
* [ ] Migrações do Flyway rodam automaticamente no boot do backend e criam o esquema inicial no PostgreSQL.
* [ ] Usuário consegue criar conta, realizar login pelo frontend Angular e receber o cookie `JSESSIONID`.
* [ ] O registro da sessão é salvo e verificado na tabela `spring_session` do banco PostgreSQL.
* [ ] Usuário deslogado é bloqueado pelo `AuthGuard` ao tentar acessar rotas privadas do Angular.