# PediuPatrão

Sistema de gestão para pizzaria com controle de acesso por perfil, gestão de pedidos com máquina de estados e auditoria persistente das operações.

## Tecnologias

- Java 21
- Spring Boot 3.4
- Spring Security 6
- Spring Data MongoDB
- Thymeleaf (com dialeto de segurança `sec:` e Bootstrap)
- Maven

## Perfis de acesso

| Perfil | Permissões |
|--------|------------|
| **ADMIN** | Administrar usuários e perfis, consultar pedidos, visualizar auditoria |
| **GERENTE** | Gerenciar pedidos, aplicar descontos, cancelar pedidos, consultar clientes, visualizar auditoria |
| **ATENDENTE** | Cadastrar e atualizar clientes, registrar pedidos e acompanhar o andamento |

A autorização é aplicada no backend em duas camadas: regras de rota no `SecurityConfig` e `@PreAuthorize` nos serviços. Descontos e cancelamentos são exclusivos do GERENTE, inclusive em relação ao ADMIN. A proteção CSRF está habilitada nos formulários.

## Pré-requisitos

- JDK 21 e Maven 3.9+
- Acesso a um banco MongoDB (local ou Atlas)

## Configuração do banco

A string de conexão fica na variável de ambiente `MONGODB_URI` (não é versionada, para não expor credenciais). Copie o exemplo e preencha com os seus dados:

```bash
cp .env.example .env
# edite o .env com a sua URI do MongoDB
```

Se `MONGODB_URI` não for definida, a aplicação usa `mongodb://localhost:27017/pizzaria` por padrão.

## Como executar

```bash
./scripts/run.sh
```

O script carrega o `.env` e inicia a aplicação. Alternativamente:

```bash
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

Acesse `http://localhost:8080`. Na primeira execução são criados três usuários e alguns produtos de exemplo. Para subir um MongoDB local sem instalar nada, use `scripts/mongo-local.sh`.

## Usuários de teste

| Login | Senha | Perfil |
|-------|-------|--------|
| admin | teste123 | ADMIN |
| gerente | gerente123 | GERENTE |
| atendente | atendente123 | ATENDENTE |

## Fluxo de status do pedido

```
RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA / RETIRADO → FINALIZADO
```

- A data/hora de entrada e o responsável pelo registro são gravados em `RECEBIDO`.
- A data/hora e o responsável pela saída são gravados em `SAIU PARA ENTREGA / RETIRADO`.
- Transições fora dessa sequência são rejeitadas.
- `CANCELADO` é um estado excepcional do GERENTE, permitido apenas antes da saída e mediante justificativa. Após o cancelamento o pedido é congelado.

## Requisitos implementados

- **RF01 — Usuários e permissões:** perfis `ADMIN`, `GERENTE` e `ATENDENTE`; `UsuarioDetailsService` passa a usar o perfil real; autorização no backend via `SecurityConfig` e `@PreAuthorize`; administração de usuários restrita ao ADMIN com bloqueio de autoelevação; CSRF habilitado.
- **RF02 — Cadastro de pedidos:** itens, quantidades, preços, desconto, valor total, responsável pelo registro e data/hora de entrada. O total é calculado no servidor; a requisição não define o total.
- **RF03 — Entrada, saída e transições:** máquina de estados estrita (`StatusPedido`) com registro de entrada e de saída e bloqueio de transições inválidas.
- **RF04 — Cancelamentos e descontos:** operações exclusivas do GERENTE; desconto percentual limitado a 20% e permitido apenas antes da saída; cancelamento com justificativa, histórico preservado e pedido congelado.
- **RF05 — Auditoria:** coleção `auditorias` no MongoDB com usuário, perfil, data/hora, operação, entidade e identificador, campos alterados (valor anterior e novo) e justificativa. Audita inclusão de pedidos, mudança de status, desconto, cancelamento e alterações de clientes (logradouro e telefone). Os registros não têm rota de alteração ou exclusão e não armazenam senhas. Consulta restrita a ADMIN e GERENTE.

## Funcionalidades adicionais

- **Controle de estoque:** cada produto possui quantidade em estoque. O estoque é baixado ao registrar o pedido (com bloqueio quando insuficiente) e devolvido no cancelamento.
- **Forma de pagamento:** o pedido registra a forma de pagamento (dinheiro, Pix, cartão). Para dinheiro, o atendente informa o valor recebido e o sistema calcula o troco no servidor, bloqueando valores menores que o total.

## Principais alterações em relação ao projeto base

- `Usuario`/`UsuarioDetailsService`: autenticação com os três perfis reais em vez de `USER` fixo.
- `SecurityConfig`: CSRF habilitado, autorização por perfil, `@EnableMethodSecurity` e página de acesso negado.
- `Pedido`/`PedidoService`: entidade enriquecida (itens, valores, responsáveis, datas), cálculo no servidor, máquina de estados, desconto e cancelamento.
- `ClienteService`: auditoria das alterações cadastrais.
- Novos componentes de auditoria: `Auditoria`, `AuditoriaRepository` (somente leitura), `AuditoriaService`, `AuditoriaController` e telas.
- Telas de pedidos e auditoria, e ajustes nas listas com `sec:authorize`.

## Testes

```bash
mvn test
```

Cobrem a máquina de estados e as regras do `PedidoService` (cálculo no servidor, limite de desconto, bloqueio após a saída, transições inválidas e congelamento após o cancelamento).
