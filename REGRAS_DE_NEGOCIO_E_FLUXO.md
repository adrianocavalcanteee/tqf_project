# Especificação Oficial de Regras de Negócio, Arquitetura e Fluxos de Dados (TQFapp)

Este documento especifica a **Arquitetura de Dados**, **Regras de Negócio**, **Matriz de Permissões** e **Gaps de Integração** do aplicativo **TQFapp**, servindo como guia definitivo para a conexão com o banco de dados de produção (Supabase PostgREST + Auth).

---

## 👥 1. Perfis e Permissões do Sistema

O sistema opera com dois atores principais identificados pelo campo boolean `is_provider` na entidade `User` (`public.usuarios`):

| Funcionalidade | Contratante (Cliente) | Prestador de Serviços |
| :--- | :---: | :---: |
| **Criar conta / Autenticação (Auth)** | ✅ | ✅ |
| **Buscar Prestadores no Mapa / Vitrine (PINs)** | ✅ | ❌ |
| **Filtrar por Categoria, Avaliação e Proximidade** | ✅ | ❌ |
| **Visualizar Perfil Público do Prestador** | ✅ | ✅ |
| **Solicitar Novo Serviço (`dialog_request_service`)** | ✅ | ❌ |
| **Receber Chamados na Região (`ProviderHomeFragment`)** | ❌ | ✅ |
| **Aceitar Solicitação (`PENDENTE` ➔ `EM_ANDAMENTO`)** | ❌ | ✅ |
| **Recusar Solicitação (`PENDENTE` ➔ `RECUSADO`)** | ❌ | ✅ |
| **Receber Sugestão da Mesma Categoria ao Ter Chamado Recusado** | ✅ | ❌ |
| **Acesso ao WhatsApp Nativo (Redirecionamento)** | ❌ (Recebe contato) | ✅ (Inicia contato) |
| **Fechar / Concluir Serviço (`EM_ANDAMENTO` ➔ `CONCLUIDO`)** | ❌ | ✅ |
| **Aba "Histórico de Serviços" (Apenas `CONCLUIDO`)** | ✅ | ❌ |
| **Aba "Contratações em Andamento" (Apenas `EM_ANDAMENTO`)** | ❌ | ✅ |
| **Avaliar Atendimento Concluído (⭐ 1 a 5 + Comentário)** | ✅ | ❌ |
| **Editar Foto de Perfil e Endereço de Entrega** | ✅ | ✅ |
| **Editar Categoria, Especialidade, Bio, Portfólio e Tags** | ❌ | ✅ |

---

## 🔄 2. Fluxo Completo de Navegação (User Flows)

```
[ Login / Cadastro ] ───► [ Validação de E-mail Supabase Auth ]
        │
        ├──────────────────────────────────────────────┐
        ▼ (Se Contratante / isProvider = false)         ▼ (Se Prestador / isProvider = true)
[ Home: Mapa Manaus & Lista de Prestadores ]    [ Feed: Chamados em Aberto na Região ]
        │                                              │
        ▼ (Seleciona Prestador)                        ├──────────────────────────────┐
[ Perfil Público do Prestador ]                        ▼ (Clique "Aceitar")           ▼ (Clique "Recusar")
        │                                       [ Status: EM_ANDAMENTO ]       [ Status: RECUSADO ]
        ▼ (Clique "Solicitar Serviço")                 │                              │
[ Modal: Descrição + WhatsApp ]                        ├─► [ Abre WhatsApp do Cliente ] └─► [ Sugere Outros Prestadores ]
        │                                              │                                   da Mesma Categoria
        ▼ (Envio do Chamado)                           ▼
[ Status: PENDENTE ]                    [ Card na Aba Contratações ]
                                                       │
                                                       ▼ (Clique "Concluir Serviço")
                                                [ Status: CONCLUIDO ]
                                                       │
                                                       ▼
                                                [ Histórico do Cliente ] ───► [ Modal: Avaliar Atendimento (1-5 ⭐) ]
```

---

## 📜 3. Regras de Negócio e Ciclo de Vida do Serviço

### 3.1. Máquina de Estados do Serviço (`ServiceStatus`)

```
                 ┌──────────────┐
                 │   PENDENTE   │
                 └──────┬───────┘
                        │
          ┌─────────────┴─────────────┐
          ▼                           ▼
 ┌─────────────────┐         ┌────────────────┐
 │  EM_ANDAMENTO   │         │    RECUSADO    │
 └────────┬────────┘         └────────────────┘
          │
          ▼
 ┌─────────────────┐
 │    CONCLUIDO    │
 └─────────────────┘
```

1. **`PENDENTE`:** Criado pelo contratante via `dialog_request_service.xml`. Fica disponível no feed do prestador.
2. **`EM_ANDAMENTO`:** Ativado quando o prestador clica em *"Aceitar Solicitação"*. Libera o redirecionamento ao WhatsApp do cliente e move o card para a aba *"Contratações"* do prestador.
3. **`RECUSADO`:** Ativado quando o prestador clica em *"Recusar"*. Aciona o motor de sugestões exibindo prestadores da mesma categoria para o cliente (`dialog_suggested_providers.xml`).
4. **`CONCLUIDO`:** Ativado **exclusivamente pelo prestador** ao clicar em *"Concluir Serviço"*. Move a solicitação para o *"Histórico de Serviços"* do contratante e habilita o modal de avaliação.

---

### 3.2. Regras de Exibição, Avaliação e Preços

* **Aba Histórico (Contratante):** Exibe **estritamente** serviços com status `CONCLUIDO`.
* **Aba Contratações (Prestador):** Exibe **estritamente** serviços com status `EM_ANDAMENTO`.
* **Avaliação de Atendimento:** Permitida somente para o Contratante em serviços `CONCLUIDO`. Grava `reviewer_id = currentUserId` (cliente) e `reviewed_id = service.providerId` (prestador) nas tabelas `public.avaliacoes` e Room.
* **Preço:** Exibido em formato de moeda nacional (`R$ XXX,XX`).

---

## 🚨 4. Diagnóstico de Lacunas para Integração com Backend Real

1. **Push Notifications (FCM / WebSockets):**
   - Disparar notificação remota ao prestador quando um novo chamado `PENDENTE` for criado na sua região.
   - Disparar notificação ao cliente quando o prestador alterar o status para `EM_ANDAMENTO` ou `RECUSADO`.
2. **Upload de Mídias em Nuvem (Supabase Storage / Bucket `tqf-media`):**
   - O fluxo do código lê URIs de imagem. Em produção, enviar arquivos para o bucket do Supabase Storage e armazenar URLs públicas em `profile_picture_url` e `portfolio_urls`.
