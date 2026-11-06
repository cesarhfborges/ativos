# Etapa 6: Administração e Permissões

## Objetivo
Separar quem usa o sistema de quem o administra, com um local único para gerir contas, catálogo de ativos e configurações gerais.

## Papéis

| Papel | Acesso |
| --- | --- |
| `USER` | A própria carteira, extrato, projeções e perfil |
| `ADMIN` | O mesmo, mais a área de administração |

A permissão é garantida no backend: `/api/admin/**` exige `ROLE_ADMIN`. No frontend, o menu e o `adminGuard` só decidem o que mostrar.

**O admin não vê carteiras alheias.** A área de administração mostra totais e contagens, nunca as posições ou operações de um usuário.

### Primeiro administrador
A variável `APP_ADMIN_EMAIL` (ou `app.admin-email` no `application-local.yml`) promove a conta com esse e-mail ao iniciar. A conta precisa existir: crie-a pela tela de registro e reinicie. Depois disso, os papéis são geridos pela tela de usuários.

### Sessões
O papel é gravado na sessão no login, e as sessões são indexadas pelo e-mail. Por isso, mudar papel, e-mail, situação ou senha de uma conta encerra as sessões dela, e a pessoa precisa entrar de novo.

## Regras
* O sistema precisa de pelo menos um administrador ativo: rebaixar ou desativar o último é recusado.
* Um admin não pode desativar a própria conta.
* Contas são desativadas, nunca apagadas, porque carteiras e operações dependem delas. Conta desativada não consegue entrar, e a mensagem é a mesma de senha errada.
* Ativo só pode ser removido do catálogo se não tiver nenhuma operação registrada, inclusive estornada.
* Segredos, como `BRAPI_TOKEN` e as credenciais do banco, ficam nas variáveis de ambiente. A tela informa apenas se o token está definido.

## Telas

| Rota | Conteúdo |
| --- | --- |
| `/admin` | Totais, última coleta, símbolos sem cotação ou sem atualização há mais de 2 horas. Ações: coletar cotações e recalcular posições |
| `/admin/usuarios` | Busca, criação e edição de contas, papel, ativar ou desativar, nova senha e encerrar sessões |
| `/admin/ativos` | Catálogo com tipo, verificação e uso. Corrigir nome e tipo, verificar no provedor e remover |
| `/admin/configuracoes` | Configurações gerais e informações do ambiente |
| `/admin/auditoria` | Registro das ações administrativas |

## Configurações gerais
A tabela `app_settings` guarda só o que o admin alterou; sem registro, vale o `application.yml`. Cada chave é o nome da propriedade correspondente. As alterações valem na hora, e os valores são validados por faixa no backend.

| Grupo | Ajustes |
| --- | --- |
| Acesso | Cadastro público ligado ou desligado |
| Coleta de cotações | Coleta automática ligada ou pausada; intervalo mínimo da atualização manual |
| Projeções | Histórico mínimo por ativo; limites do retorno anual medido |
| Premissas de referência | Retorno e volatilidade de ações e cripto |
| Cenários | Bônus do otimista, penalidade do pessimista e quedas imediatas |
| Limites de risco | Exposição máxima a cripto e concentração máxima em um ativo |

Os módulos leem as configurações pela interface `AppSettings`, do `shared-kernel`, sem depender do `core-api`.

## Auditoria
A tabela `admin_audit_log` registra quem fez o quê, em qual alvo e quando, na mesma transação da ação. Guarda o e-mail do momento e nunca recebe senhas.

## Recalcular posições
Refaz preço médio e lucro das vendas de todas as carteiras a partir das operações, com as regras atuais. Execute uma vez depois de atualizar a partir de uma versão em que a taxa de compra ficava fora do custo. Snapshots antigos não são reescritos, então o histórico pode mostrar um pequeno degrau no valor investido.

## Fora de Escopo
Permissões mais granulares que admin e usuário, troca de senha obrigatória no próximo login e visualização de carteiras por administradores.
