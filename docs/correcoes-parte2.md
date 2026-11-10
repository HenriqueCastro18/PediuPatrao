# Correções da Parte 2 — Pediu Patrão

Este documento descreve como foram tratados os três pontos apontados na avaliação
da **Prática M1 Parte 2**:

> "Apresenta uma falha grave de segurança, permitindo redefinir senhas sem
> verificar a identidade do usuário. Também unifica indevidamente os estados de
> entrega e retirada e possui fragilidades na administração de usuários."

---

## 1. Falha grave de segurança — redefinição de senha sem verificar identidade

**Antes:** a rota pública `POST /esqueci-senha` recebia um `username` e uma nova
senha e trocava a senha de qualquer usuário, **sem qualquer verificação de
identidade**. Bastava saber o nome de usuário para sequestrar a conta.

**Depois:** a redefinição passou a verificar a identidade por dois caminhos
legítimos, e o endpoint vulnerável foi removido.

1. **Troca de senha autenticada** (`/alterar-senha`): exige a **senha atual**.
   Implementada em `UsuarioService.alterarPropriaSenha(senhaAtual, novaSenha, confirmacao)`,
   que valida a senha vigente com `PasswordEncoder.matches` antes de permitir a troca.

2. **Esqueci minha senha por e-mail** (`/esqueci-senha` → `/redefinir-senha`): o
   usuário informa o **e-mail cadastrado**; o sistema gera um **token aleatório de
   uso único, com validade** (30 min), e envia um link por e-mail. A nova senha só
   é aceita mediante um token válido e não expirado. A identidade é verificada pela
   **posse da caixa de e-mail**.
   - Token gerado com `SecureRandom` (32 bytes, Base64 URL-safe).
   - Resposta genérica ("se o e-mail existir, enviamos o link") para **não revelar**
     quais e-mails estão cadastrados (evita enumeração de usuários).
   - Envio via SMTP configurável por variáveis de ambiente (`EmailService`).

3. A redefinição da senha de **terceiros** continua restrita ao **ADMIN**, pela
   tela de administração de usuários.

Arquivos: `UsuarioService`, `EmailService`, `HomeController`, `SecurityConfig`,
`templates/alterar-senha.html`, `templates/esqueci-senha.html`,
`templates/redefinir-senha.html`.

---

## 2. Estados de entrega e retirada unificados indevidamente

**Antes:** havia um único estado `SAIU_PARA_ENTREGA_RETIRADO` ("Saiu para entrega /
Retirado"), misturando duas situações distintas — **entrega** (delivery) e
**retirada no balcão**.

**Depois:**

- Novo enum **`TipoEntrega`** (`ENTREGA`, `RETIRADA`), definido no pedido no momento
  do cadastro.
- O estado único foi **separado** em dois: **`SAIU_PARA_ENTREGA`** e **`RETIRADO`**.
- A **máquina de estados** passou a ser ciente da modalidade: a partir de `PRONTO`,
  um pedido de entrega segue para `SAIU_PARA_ENTREGA` e um de retirada para
  `RETIRADO`; ambos finalizam em `FINALIZADO`.

```
RECEBIDO → EM_PREPARACAO → PRONTO → (ENTREGA)  SAIU_PARA_ENTREGA → FINALIZADO
                                   → (RETIRADA) RETIRADO          → FINALIZADO
```

Arquivos: `entity/TipoEntrega.java`, `entity/StatusPedido.java`, `entity/Pedido.java`,
`service/PedidoService.java`, `PedidoWebController`, `templates/pedidos/*`.
Testado em `StatusPedidoTest` (fluxos de entrega e de retirada não se confundem) e
`PedidoServiceTest`.

---

## 3. Fragilidades na administração de usuários

**Antes:** o cadastro/edição não validava duplicidade de nome de usuário, perfil
inválido, tamanho de senha, e não havia proteção contra remover/rebaixar o último
administrador (risco de *lockout* do sistema).

**Depois (`UsuarioService`):**

- **Nome de usuário**: obrigatório, mínimo de 3 caracteres e **único** (na criação e
  na edição).
- **Senha**: mínimo de 4 caracteres (validado na criação e quando alterada).
- **Perfil**: restrito ao conjunto válido `{ADMIN, GERENTE, ATENDENTE}` e normalizado.
- **E-mail**: formato validado quando informado.
- **Proteção de *lockout***: impede **excluir** ou **rebaixar** o **último ADMIN**.
- Mantidas as travas já existentes: não excluir o próprio usuário e não alterar o
  próprio perfil.

Testado em `UsuarioServiceTest` (duplicidade, perfil inválido, senha curta,
proteção do último admin, troca de senha com senha atual errada, reset por token
válido/expirado/inexistente).

---

## Execução dos testes

```
mvn test
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
