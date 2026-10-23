# Serviço Voluntário Gratificado (SVG)

Sistema para gerenciar o Serviço Voluntário Gratificado da PMDF: planejamento de cotas, captação de policiais para os serviços, seleção por ranking, registro de presenças e controle do que foi realizado.

> **Status:** em desenvolvimento. Backend (esquema, regras de negócio, segurança e API REST de policial e captação) e telas do front existem. Ainda não há cadastro de serviços pela API nem dados iniciais de desenvolvimento; o front roda com API e login em memória por padrão.

## Estrutura

| Pasta | Conteúdo |
| --- | --- |
| `src/` | Backend: Spring Boot 4.1, Java 17, JPA, Flyway e PostgreSQL |
| `frontend/` | Front: Angular 21, Vitest, login OIDC (`oidc-client-ts`) |
| `dev/keycloak/` | Realm `pmdf` importado pelo Keycloak de desenvolvimento |
| `docker-compose.yml` | PostgreSQL 17 e Keycloak 26 para uso local |

## Pré-requisitos

- JDK 17 ou superior
- Node.js e npm (o front usa `npm@11`)
- Docker, para o PostgreSQL e o Keycloak locais (e para os testes que usam Testcontainers)

## Como rodar

### 1. Infraestrutura local

```
docker compose up -d
```

Sobe o PostgreSQL em `localhost:5432` (banco, usuário e senha `svg`) e o Keycloak em `http://localhost:8081`.

### 2. Backend

```
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. O esquema do banco é criado pelo Flyway (`src/main/resources/db/migration`) e o Hibernate apenas o valida.

Variáveis de ambiente opcionais (os padrões servem para o `docker-compose.yml`):

| Variável | Padrão |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/svg` |
| `DB_USER` | `svg` |
| `DB_PASSWORD` | `svg` |
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/pmdf` |

### 3. Front

```
cd frontend
npm install
npm start
```

Abre em `http://localhost:4200`. Em desenvolvimento (`src/environments/environment.development.ts`) o front usa `useMocks: true`, ou seja, API e login em memória, sem precisar do backend nem do Keycloak. Para usar os serviços reais, troque para `false`. O proxy de `/api` aponta para `http://localhost:8080` (`frontend/proxy.conf.json`).

## Testes

```
./mvnw test          # backend
cd frontend && npm test   # front (Vitest)
```

`ApiIntegracaoTest` percorre a API contra um PostgreSQL real embutido (zonky `embedded-postgres`), sem precisar de Docker. `ServicoVoluntarioApplicationTests` usa Testcontainers e é ignorado sem Docker.

## Autenticação

A API é *resource server* do SSO institucional (Keycloak, realm `pmdf`) e não tem login local. Os papéis vêm do claim `realm_access.roles` do JWT:

`POLICIAL`, `RESP_CAPTACAO`, `GESTOR_UNIDADE`, `COORD_CENTRAL`, `COMANDO`, `CONTROLE`

O front usa Authorization Code com PKCE (client público `svg-web`).

Usuários de teste do realm de desenvolvimento (senha `teste`):

| Usuário | Papéis |
| --- | --- |
| `policial.teste` | `POLICIAL` |
| `captacao.teste` | `POLICIAL`, `RESP_CAPTACAO` |

As credenciais do `docker-compose.yml` e do realm são **somente para desenvolvimento**.

## API

Base `/api/v1`. Erros vêm como `ProblemDetail`; a mensagem fica em `detail` e, quando há violações de regra, em `violacoes`.

| Perfil | Endpoint |
| --- | --- |
| `POLICIAL` | `GET /servicos`, `GET /servicos/{id}` (com a elegibilidade do policial logado) |
| `POLICIAL` | `POST /servicos/{id}/candidaturas`, `GET /minhas-candidaturas`, `DELETE /candidaturas/{id}`, `GET /minha-cota?anoMes=AAAA-MM` |
| `RESP_CAPTACAO` | `GET /captacao/servicos`, `GET /captacao/servicos/{id}`, `GET /captacao/servicos/{id}/ranking` |
| `RESP_CAPTACAO` | `POST /captacao/servicos/{id}/abrir`, `.../encerrar`, `.../selecionar` |
| `RESP_CAPTACAO` | `POST /execucoes/{candidaturaId}` (presença ou falta) |

O policial é identificado pelo claim `matricula` do token, que deve existir em `policial`. O responsável pela captação atua nas unidades de `papel_unidade` (perfil `RESP_CAPTACAO`); sem linha ali, apenas na própria unidade. O encerramento da captação grava a posição e as cotas de cada candidato; a seleção revalida a elegibilidade e exige justificativa (mínimo de 10 caracteres) quando diverge do ranking. O teto individual mensal só vale se houver o parâmetro `TETO_INDIVIDUAL_COTAS_MES` em `parametro`.

## Regras de negócio

Implementadas em Java puro no pacote `com.nicolas.servicovoluntario.regras`, com testes unitários:

- **Cota** (`CalculoCota`, Decreto DF 39.627/2019): uma cota equivale a 8 horas; a jornada pode ser fracionada entre 4 e 12 horas, com valor proporcional; fração de hora de 30 minutos ou mais conta como uma hora.
- **Elegibilidade** (`RegrasElegibilidade`, `AvaliadorElegibilidade`): choque com a escala regular, descanso mínimo após a escala, choque com outro serviço já selecionado, afastamento, bloqueio, impedimento por função de natureza especial (Lei 10.486/2002, art. 3º, VII), círculo hierárquico da vaga (praça ou oficial) e teto individual mensal de cotas.
- **Ranking** (`Ranking`): quem tem menos SVG no mês anterior e no atual tem prioridade, com desempate pelo mês atual, antiguidade e matrícula. **Critério provisório**: o art. 16 da Portaria PMDF 1.280/2022 ainda não foi consultado.

## Telas do front

- **Policial:** vagas, detalhe da vaga, candidaturas e cota.
- **Captação** (`/captacao`, restrita ao perfil de captação): lista de serviços da unidade, seleção por ranking com justificativa obrigatória quando diverge do ranking, limite de vagas e lançamento de presença ou falta.

## Pendências conhecidas

- Cadastro e edição de serviços e vagas pela API (hoje só por SQL) e dados iniciais para desenvolvimento.
- Fechamento automático da captação em `captacao_fecha_em` (hoje a candidatura apenas respeita o prazo).
- Cancelamento de candidatura já selecionada, correção de presença lançada, bloqueio por falta e exportação de pagamento: faltam os parâmetros da Portaria 1.280/2022.
- Competência de pagamento (hoje o mês em que o serviço termina) e critério do ranking são provisórios.
- Login OIDC e `HttpSvgApi` ainda não foram exercitados contra Keycloak e backend reais.
