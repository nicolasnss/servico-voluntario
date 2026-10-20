-- Esquema inicial do SVG (Serviço Voluntário Gratificado).
-- Fontes: Decreto DF 39.627/2019 (e alterações), Lei 10.486/2002 art. 3º VII-VIII,
-- Anexo I da Portaria PMDF 1.280/2022 (ficha de inscrição) e Regimento do DCC (SSVG).
-- Valores marcados como PROVISORIO dependem do texto da Portaria 1.280/2022 e devem ser confirmados.

CREATE TABLE unidade_militar (
    id             BIGSERIAL PRIMARY KEY,
    nome           VARCHAR(120) NOT NULL,
    sigla          VARCHAR(20),
    tipo           VARCHAR(20)  NOT NULL,
    unidade_pai_id BIGINT REFERENCES unidade_militar (id)
);

CREATE TABLE policial (
    id              BIGSERIAL PRIMARY KEY,
    keycloak_sub    VARCHAR(64) UNIQUE,
    matricula       VARCHAR(20)  NOT NULL UNIQUE,
    nome            VARCHAR(150) NOT NULL,
    nome_guerra     VARCHAR(60),
    posto_graduacao VARCHAR(30)  NOT NULL,
    circulo         VARCHAR(10)  NOT NULL CHECK (circulo IN ('PRACA', 'OFICIAL')),
    data_admissao   DATE         NOT NULL,
    unidade_id      BIGINT       NOT NULL REFERENCES unidade_militar (id),
    situacao        VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    email           VARCHAR(150)
);

-- Papéis vêm do Keycloak; aqui fica apenas o escopo de unidade (NULL = todas as unidades).
CREATE TABLE papel_unidade (
    id           BIGSERIAL PRIMARY KEY,
    keycloak_sub VARCHAR(64) NOT NULL,
    perfil       VARCHAR(30) NOT NULL,
    unidade_id   BIGINT REFERENCES unidade_militar (id)
);
CREATE UNIQUE INDEX uq_papel_unidade ON papel_unidade (keycloak_sub, perfil, COALESCE(unidade_id, 0));

-- Orçamento anual de cotas da corporação e sua distribuição pelo Comando.
CREATE TABLE orcamento_cotas (
    ano                  INT PRIMARY KEY,
    teto_base            INT NOT NULL,
    acrescimo_temporario INT NOT NULL DEFAULT 0
);

CREATE TABLE distribuicao_cotas (
    id                 BIGSERIAL PRIMARY KEY,
    ano                INT           NOT NULL REFERENCES orcamento_cotas (ano),
    unidade_id         BIGINT        NOT NULL REFERENCES unidade_militar (id),
    quantidade         NUMERIC(10,3) NOT NULL CHECK (quantidade >= 0),
    restrito_a_circulo VARCHAR(10) CHECK (restrito_a_circulo IN ('PRACA', 'OFICIAL'))
);
CREATE UNIQUE INDEX uq_distribuicao_cotas
    ON distribuicao_cotas (ano, unidade_id, COALESCE(restrito_a_circulo, ''));

-- Valor da cota com vigência (Decreto 39.627, art. 3º).
CREATE TABLE valor_cota (
    id            BIGSERIAL PRIMARY KEY,
    valor         NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    vigente_desde DATE          NOT NULL UNIQUE
);

CREATE TABLE servico (
    id                       BIGSERIAL PRIMARY KEY,
    ordem_servico_numero     VARCHAR(30),
    titulo                   VARCHAR(150) NOT NULL,
    descricao                TEXT,
    local                    VARCHAR(200) NOT NULL,
    origem_demanda           VARCHAR(150),
    unidade_id               BIGINT       NOT NULL REFERENCES unidade_militar (id),
    inicio                   TIMESTAMPTZ  NOT NULL,
    fim                      TIMESTAMPTZ  NOT NULL,
    horas_contadas           SMALLINT     NOT NULL CHECK (horas_contadas BETWEEN 4 AND 12),
    captacao_abre_em         TIMESTAMPTZ,
    captacao_fecha_em        TIMESTAMPTZ,
    captacao_aberta_por      VARCHAR(150),
    status                   VARCHAR(30)  NOT NULL DEFAULT 'RASCUNHO',
    criado_por               VARCHAR(64),
    criado_em                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (fim > inicio),
    CHECK (captacao_fecha_em IS NULL OR captacao_abre_em IS NULL OR captacao_fecha_em > captacao_abre_em)
);
CREATE INDEX idx_servico_inicio_status ON servico (inicio, status);

CREATE TABLE vaga (
    id                 BIGSERIAL PRIMARY KEY,
    servico_id         BIGINT      NOT NULL REFERENCES servico (id) ON DELETE CASCADE,
    funcao             VARCHAR(80),
    tipo_funcao        VARCHAR(12) NOT NULL DEFAULT 'EXECUCAO' CHECK (tipo_funcao IN ('EXECUCAO', 'SUPERVISAO')),
    quantidade         INT         NOT NULL CHECK (quantidade > 0),
    circulos_permitidos VARCHAR(10)[]
);

CREATE TABLE candidatura (
    id                       BIGSERIAL PRIMARY KEY,
    vaga_id                  BIGINT      NOT NULL REFERENCES vaga (id),
    policial_id              BIGINT      NOT NULL REFERENCES policial (id),
    status                   VARCHAR(30) NOT NULL DEFAULT 'CANDIDATA',
    inscrita_em              TIMESTAMPTZ NOT NULL DEFAULT now(),
    declaracao_art5_aceita   BOOLEAN     NOT NULL,
    -- snapshot gravado no encerramento da captação, usado no ranking e na auditoria
    cotas_mes_anterior       NUMERIC(8,3),
    cotas_mes_atual          NUMERIC(8,3),
    posicao_ranking          INT,
    divergiu_do_ranking      BOOLEAN     NOT NULL DEFAULT FALSE,
    decidida_por             VARCHAR(64),
    decidida_em              TIMESTAMPTZ,
    justificativa            TEXT,
    UNIQUE (vaga_id, policial_id)
);
CREATE INDEX idx_candidatura_policial ON candidatura (policial_id, status);

-- Escala ordinária: o SVG só pode ser prestado na folga (Decreto 39.627, art. 2º).
CREATE TABLE escala_regular (
    id          BIGSERIAL PRIMARY KEY,
    policial_id BIGINT      NOT NULL REFERENCES policial (id),
    inicio      TIMESTAMPTZ NOT NULL,
    fim         TIMESTAMPTZ NOT NULL,
    CHECK (fim > inicio)
);
CREATE INDEX idx_escala_policial_periodo ON escala_regular (policial_id, inicio, fim);

CREATE TABLE afastamento (
    id          BIGSERIAL PRIMARY KEY,
    policial_id BIGINT      NOT NULL REFERENCES policial (id),
    tipo        VARCHAR(30) NOT NULL,
    data_inicio DATE        NOT NULL,
    data_fim    DATE
);

-- Impedimentos como a função de natureza especial (Lei 10.486, art. 3º VII).
CREATE TABLE impedimento_policial (
    id            BIGSERIAL PRIMARY KEY,
    policial_id   BIGINT      NOT NULL REFERENCES policial (id),
    tipo          VARCHAR(40) NOT NULL,
    vigente_desde DATE        NOT NULL,
    vigente_ate   DATE
);

-- Realizado: base do pagamento (só se paga o efetivamente prestado).
CREATE TABLE execucao (
    id                    BIGSERIAL PRIMARY KEY,
    candidatura_id        BIGINT        NOT NULL UNIQUE REFERENCES candidatura (id),
    situacao              VARCHAR(20)   NOT NULL CHECK (situacao IN ('PRESENTE', 'FALTOU', 'FALTA_JUSTIFICADA')),
    horas_contadas        SMALLINT      NOT NULL CHECK (horas_contadas BETWEEN 0 AND 12),
    cotas                 NUMERIC(5,3)  NOT NULL,
    valor_cota_id         BIGINT        NOT NULL REFERENCES valor_cota (id),
    competencia_pagamento CHAR(7)       NOT NULL,
    lancado_por           VARCHAR(64)   NOT NULL,
    lancado_em            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE bloqueio (
    id          BIGSERIAL PRIMARY KEY,
    policial_id BIGINT      NOT NULL REFERENCES policial (id),
    motivo      VARCHAR(40) NOT NULL,
    inicio      DATE        NOT NULL,
    fim         DATE        NOT NULL,
    execucao_id BIGINT REFERENCES execucao (id),
    CHECK (fim >= inicio)
);

CREATE TABLE exportacao_pagamento (
    id           BIGSERIAL PRIMARY KEY,
    competencia  CHAR(7)       NOT NULL,
    total_cotas  NUMERIC(12,3) NOT NULL,
    total_valor  NUMERIC(14,2) NOT NULL,
    gerada_por   VARCHAR(64)   NOT NULL,
    gerada_em    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    conteudo     TEXT          NOT NULL
);

-- Histórico anterior ao sistema (Anexo I em papel, sistema de faltas), necessário ao ranking desde o dia 1.
CREATE TABLE historico_svg_importado (
    policial_id BIGINT       NOT NULL REFERENCES policial (id),
    ano_mes     CHAR(7)      NOT NULL,
    cotas       NUMERIC(8,3) NOT NULL,
    origem      VARCHAR(40)  NOT NULL,
    PRIMARY KEY (policial_id, ano_mes)
);

-- Parâmetros com vigência: números da Portaria vivem aqui, não no código.
CREATE TABLE parametro (
    chave         VARCHAR(60)  NOT NULL,
    valor         VARCHAR(100) NOT NULL,
    vigente_desde DATE         NOT NULL,
    observacao    VARCHAR(200),
    PRIMARY KEY (chave, vigente_desde)
);

-- Trilha de auditoria append-only (exigência prática diante da fiscalização externa).
CREATE TABLE auditoria (
    id             BIGSERIAL PRIMARY KEY,
    usuario_sub    VARCHAR(64),
    acao           VARCHAR(60) NOT NULL,
    entidade       VARCHAR(40),
    entidade_id    BIGINT,
    valor_anterior JSONB,
    valor_novo     JSONB,
    ip             VARCHAR(45),
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION auditoria_imutavel() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A tabela auditoria é append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_imutavel();

-- Dados de referência.
-- Decreto 48.415, de 25/03/2026, altera o art. 3º do Decreto 39.627.
INSERT INTO valor_cota (valor, vigente_desde) VALUES (760.00, DATE '2026-03-25');

-- Teto anual da PMDF (Decreto 39.627, art. 4º, I, na redação do Decreto 48.415/2026).
-- O acréscimo temporário de 2026 veio de pesquisa do solicitante (Decreto 48.896/2026): CONFIRMAR.
INSERT INTO orcamento_cotas (ano, teto_base, acrescimo_temporario) VALUES (2026, 157894, 94744);

-- PROVISORIO: referência da norma antiga do CBMDF. Confirmar na Portaria PMDF 1.280/2022.
INSERT INTO parametro (chave, valor, vigente_desde, observacao) VALUES
    ('DESCANSO_HORAS_APOS_ESCALA_12H', '6',  DATE '2026-01-01', 'PROVISORIO - referencia CBMDF'),
    ('DESCANSO_HORAS_APOS_ESCALA_24H', '12', DATE '2026-01-01', 'PROVISORIO - referencia CBMDF');
-- Sem seed para: teto individual de cotas, prazo de cancelamento e dias de bloqueio por falta.
