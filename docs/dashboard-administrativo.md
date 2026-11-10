# Dashboard Administrativo — Pediu Patrão

Documento da atividade **Dashboard Administrativo (10 pts)**. Descreve os requisitos,
a API REST projetada e implementada, a documentação dos endpoints, as regras de
negócio das métricas e as evidências de testes.

- **Branch (gitflow):** `feature/dashboard-administrativo`
- **Stack:** Java 21, Spring Boot 3.4, Spring Security 6, Spring Data MongoDB, Thymeleaf
- **Dashboard (front-end):** AdminLTE 3 / Bootstrap 5 + Chart.js 2.9.4 (consumo da API via `fetch`)
- **Acesso:** perfis **ADMIN** e **GERENTE** (autenticados por sessão)
- **URL do painel:** `/dashboard` · **Base da API:** `/api/dashboard`

---

## 1. Requisitos

### 1.1 Requisitos Funcionais

| ID | Requisito | Como foi atendido |
|----|-----------|-------------------|
| **RF01** | Indicadores gerais de vendas | KPIs: faturamento, pedidos válidos, ticket médio, itens vendidos, pedidos cancelados, desconto concedido, total de pedidos e forma de pagamento mais usada. Endpoint `GET /api/dashboard/indicadores`. |
| **RF02** | Análise de vendas (gráficos) | Série temporal de faturamento/pedidos por dia ou por mês (gráfico de linha). Endpoint `GET /api/dashboard/vendas-periodo`. |
| **RF03** | Situação dos pedidos | Distribuição por status (gráfico de rosca + tabela). Endpoint `GET /api/dashboard/situacao-pedidos`. |
| **RF04** | Produtos mais vendidos | Ranking por quantidade e faturamento (gráfico de barras). Endpoint `GET /api/dashboard/produtos-mais-vendidos`. |
| **RF05** | Filtros de consulta | Período (início/fim), situação, forma de pagamento e agrupamento (dia/mês). Opções servidas por `GET /api/dashboard/filtros`. |
| **RF06** | Integração com a API | O painel não acessa o banco diretamente: consome exclusivamente os endpoints REST via `fetch`. `GET /api/dashboard/resumo` entrega tudo em uma chamada. |
| **RF07** | Atualização e tratamento de dados | Botões **Aplicar** e **Atualizar**; tratamento de período vazio, valores nulos, pedidos cancelados (excluídos do faturamento) e estados sem dados. |

### 1.2 Requisitos Não Funcionais

| ID | Requisito |
|----|-----------|
| **RNF01** | Segurança: endpoints restritos a ADMIN/GERENTE via Spring Security (`@PreAuthorize` + regras de rota). Requisições não autenticadas são redirecionadas ao login. |
| **RNF02** | Respostas em JSON com códigos HTTP adequados (200, 400, 403). |
| **RNF03** | Tratamento de erros padronizado (`timestamp`, `status`, `erro`, `mensagem`). |
| **RNF04** | Desempenho: uma única leitura por período no banco, com agregação em memória; endpoint consolidado (`/resumo`) evita múltiplas idas ao servidor. |
| **RNF05** | Usabilidade/acessibilidade: paleta validada para daltonismo, legendas, rótulos diretos e visão em tabela além dos gráficos. |
| **RNF06** | Responsividade: layout em grid Bootstrap adaptável a celular/desktop. |

---

## 2. Arquitetura e integração

```
Navegador (/dashboard)
   │  fetch() com cookie de sessão
   ▼
DashboardApiController  (@RestController /api/dashboard)
   ▼
DashboardService        (regras de agregação, @PreAuthorize ADMIN/GERENTE)
   ▼
PedidoRepository        (Spring Data MongoDB)
   ▼
MongoDB (coleção "pedidos")
```

- **`DashboardApiController`** — expõe os endpoints REST.
- **`DashboardService`** — filtra os pedidos por período/situação/forma e calcula indicadores, série temporal, ranking e distribuições.
- **`ApiExceptionHandler`** — `@RestControllerAdvice` do pacote da API; converte exceções em JSON com o status correto.
- **`DashboardController`** — serve a página `/dashboard` (Thymeleaf) que consome a API.

### 2.1 Regras de negócio das métricas

- **Faturamento, ticket médio, itens vendidos, desconto, ranking e distribuição por pagamento** consideram apenas **pedidos não cancelados** (vendas efetivas).
- **Situação dos pedidos (RF03)** considera **todas** as situações dentro do período (inclui cancelados), pois a própria situação é a dimensão analisada.
- **Ticket médio** = faturamento ÷ pedidos válidos (0 quando não há pedidos válidos).
- **Período padrão:** quando `inicio`/`fim` não são informados, usa-se de `2000-01-01` até a data atual.
- Valores monetários são arredondados em duas casas.

---

## 3. Documentação dos endpoints

Base: `/api/dashboard` · Autenticação: sessão (ADMIN ou GERENTE) · Formato: `application/json`

### Parâmetros de filtro comuns

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
|-----------|------|-------------|-----------|---------|
| `inicio` | `date` (ISO `yyyy-MM-dd`) | Não | Data inicial (inclusiva) | `2026-10-01` |
| `fim` | `date` (ISO `yyyy-MM-dd`) | Não | Data final (inclusiva) | `2026-10-31` |
| `status` | enum `StatusPedido` | Não | Filtra por situação | `FINALIZADO` |
| `formaPagamento` | enum `FormaPagamento` | Não | Filtra por pagamento | `DINHEIRO` |
| `agrupamento` | `dia` \| `mes` | Não (padrão `dia`) | Granularidade da série | `mes` |
| `limite` / `limiteProdutos` | `int` | Não (padrão `5`) | Tamanho do ranking | `8` |

Valores de enum aceitos:
- `StatusPedido`: `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO`
- `FormaPagamento`: `DINHEIRO`, `PIX`, `CARTAO_DEBITO`, `CARTAO_CREDITO`

---

### 3.1 `GET /api/dashboard/indicadores` — RF01

Indicadores gerais do período.

**Requisição**
```
GET /api/dashboard/indicadores?inicio=2026-10-01&fim=2026-10-31
```

**Resposta `200 OK`**
```json
{
  "totalPedidos": 8,
  "pedidosValidos": 6,
  "pedidosCancelados": 2,
  "faturamentoTotal": 298.26,
  "ticketMedio": 49.71,
  "itensVendidos": 9,
  "descontoConcedido": 37.14,
  "periodoInicio": "2026-10-01",
  "periodoFim": "2026-10-31"
}
```

### 3.2 `GET /api/dashboard/vendas-periodo` — RF02

Série temporal (faturamento e nº de pedidos) agrupada por dia ou mês. Ignora cancelados.

**Requisição**
```
GET /api/dashboard/vendas-periodo?agrupamento=mes
```

**Resposta `200 OK`**
```json
[
  { "rotulo": "2026-10", "faturamento": 298.26, "pedidos": 6 }
]
```

### 3.3 `GET /api/dashboard/situacao-pedidos` — RF03

Quantidade e valor por situação (todas as situações do período).

**Requisição**
```
GET /api/dashboard/situacao-pedidos?inicio=2026-10-01&fim=2026-10-31
```

**Resposta `200 OK` (trecho)**
```json
[
  { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 0, "valorTotal": 0.0 },
  { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 6, "valorTotal": 298.26 },
  { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 2, "valorTotal": 80.0 }
]
```

### 3.4 `GET /api/dashboard/produtos-mais-vendidos` — RF04

Ranking de produtos por quantidade (desempate por faturamento). Ignora cancelados.

**Requisição**
```
GET /api/dashboard/produtos-mais-vendidos?limite=5
```

**Resposta `200 OK`**
```json
[
  { "produtoId": "6ad0069c7988b350c56fcebf", "nome": "Pizza Margherita", "quantidade": 5, "faturamento": 249.5 },
  { "produtoId": "6ad0069c7988b350c56fcec2", "nome": "Refrigerante 2L", "quantidade": 3, "faturamento": 36.0 }
]
```

### 3.5 `GET /api/dashboard/formas-pagamento`

Distribuição de vendas por forma de pagamento. Ignora cancelados.

**Resposta `200 OK`**
```json
[
  { "forma": "DINHEIRO", "descricao": "Dinheiro", "quantidade": 5, "valor": 248.36 }
]
```

### 3.6 `GET /api/dashboard/filtros` — RF05

Opções disponíveis para os seletores da interface.

**Resposta `200 OK` (trecho)**
```json
{
  "status": [ { "valor": "RECEBIDO", "descricao": "Recebido" }, { "valor": "FINALIZADO", "descricao": "Finalizado" } ],
  "formasPagamento": [ { "valor": "DINHEIRO", "descricao": "Dinheiro" }, { "valor": "PIX", "descricao": "Pix" } ],
  "tiposEntrega": [ { "valor": "ENTREGA", "descricao": "Entrega" }, { "valor": "RETIRADA", "descricao": "Retirada no balcão" } ]
}
```

### 3.7 `GET /api/dashboard/resumo` — RF06 (consolidado)

Retorna indicadores + série + situação + ranking + formas de pagamento em uma chamada.
Parâmetros: todos os comuns + `limiteProdutos`.

**Requisição**
```
GET /api/dashboard/resumo?inicio=2026-10-01&fim=2026-10-31&agrupamento=dia&limiteProdutos=8
```

**Resposta `200 OK` (estrutura)**
```json
{
  "indicadores": { "...": "ver 3.1" },
  "vendasPorPeriodo": [ { "rotulo": "2026-10-16", "faturamento": 248.36, "pedidos": 5 } ],
  "situacaoPedidos": [ { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 6, "valorTotal": 298.26 } ],
  "produtosMaisVendidos": [ { "produtoId": "...", "nome": "Pizza Margherita", "quantidade": 5, "faturamento": 249.5 } ],
  "formasPagamento": [ { "forma": "DINHEIRO", "descricao": "Dinheiro", "quantidade": 5, "valor": 248.36 } ]
}
```

---

## 4. Erros possíveis

Formato padrão de erro:
```json
{
  "timestamp": "2026-11-10T15:16:04.600154039",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Valor inválido para o parâmetro 'status': XPTO"
}
```

| Situação | HTTP | Exemplo de `mensagem` |
|----------|------|------------------------|
| Valor de enum inválido (`status`/`formaPagamento`) | `400` | `Valor inválido para o parâmetro 'status': XPTO` |
| Data em formato inválido | `400` | `Valor inválido para o parâmetro 'inicio': 10-2026` |
| `inicio` posterior a `fim` | `400` | `A data inicial não pode ser posterior à data final.` |
| Usuário sem perfil ADMIN/GERENTE | `403` | `Acesso negado ao recurso solicitado.` |
| Não autenticado | `302 → /login` | (redirecionado ao login) |
| Erro interno inesperado | `500` | `Erro interno ao processar a requisição.` |

---

## 5. Evidências de testes

### 5.1 Testes automatizados (JUnit 5 + Mockito)

`DashboardServiceTest` cobre as regras de agregação:

| Teste | Verifica |
|-------|----------|
| `indicadoresIgnoramCancelados` | Faturamento/ticket/itens desconsideram cancelados |
| `ticketMedioZeroSemPedidosValidos` | Divisão segura quando não há vendas |
| `topProdutosOrdenadosPorQuantidade` | Ranking correto e cancelados ignorados |
| `situacaoListaTodosOsStatus` | Todas as situações são retornadas |
| `vendasPorPeriodoAgrupaPorDiaEIgnoraCancelados` | Agrupamento temporal correto |
| `filtroPorFormaPagamento` | Filtro por pagamento aplicado |
| `periodoInvalidoLancaErro` | `inicio > fim` gera erro de validação |
| `resumoConsolidaTudo` | Endpoint consolidado monta todas as seções |

Execução de toda a suíte:
```
mvn test
...
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
(15 `PedidoServiceTest` + 7 `StatusPedidoTest` + 11 `UsuarioServiceTest` + 8 `DashboardServiceTest`)

### 5.2 Testes de integração manuais (API em execução, dados reais do MongoDB Atlas)

```
# login (sessão)  → 302 para "/"
# GET /api/dashboard/indicadores (não autenticado) → 302 /login   (segurança OK)

$ GET /api/dashboard/indicadores
{ "totalPedidos": 8, "pedidosValidos": 6, "pedidosCancelados": 2,
  "faturamentoTotal": 298.26, "ticketMedio": 49.71, "itensVendidos": 9,
  "descontoConcedido": 37.14, "periodoInicio": "2000-01-01", "periodoFim": "2026-11-10" }

$ GET /api/dashboard/indicadores?status=XPTO
HTTP 400 → { "mensagem": "Valor inválido para o parâmetro 'status': XPTO" }

$ GET /api/dashboard/indicadores?inicio=2026-05-10&fim=2026-05-01
HTTP 400 → { "mensagem": "A data inicial não pode ser posterior à data final." }
```

---

## 6. Como executar e acessar

1. Configure o `.env` (ver `.env.example`) com `MONGODB_URI` e, opcionalmente, as variáveis de e-mail.
2. Rode a aplicação:
   ```bash
   ./scripts/run.sh
   ```
3. Acesse `http://localhost:8080`, faça login como **admin** (perfil ADMIN) ou **gerente** (perfil GERENTE).
4. No menu lateral, abra **Dashboard** (`/dashboard`).
5. Use os filtros e o botão **Atualizar** para recalcular os indicadores.

Usuários de teste (carga inicial): `admin/teste123`, `gerente/gerente123`, `atendente/atendente123`.
O perfil ATENDENTE **não** tem acesso ao dashboard.
