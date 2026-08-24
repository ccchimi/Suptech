CREATE TABLE caso_postventa (
    id               UUID          PRIMARY KEY,
    pedido_id        VARCHAR(64)   NOT NULL,
    cliente_id       VARCHAR(64)   NOT NULL,
    tipo             VARCHAR(20)   NOT NULL,
    estado           VARCHAR(30)   NOT NULL,
    motivo           VARCHAR(500)  NOT NULL,
    monto_solicitado NUMERIC(19, 4),
    resolucion       VARCHAR(1000),
    creado_en        TIMESTAMPTZ   NOT NULL,
    actualizado_en   TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_caso_cliente ON caso_postventa (cliente_id, creado_en DESC);
CREATE INDEX idx_caso_pedido  ON caso_postventa (pedido_id);
CREATE INDEX idx_caso_estado  ON caso_postventa (estado);

CREATE TABLE caso_linea (
    caso_id        UUID         NOT NULL REFERENCES caso_postventa (id) ON DELETE CASCADE,
    sku            VARCHAR(64)  NOT NULL,
    cantidad       INTEGER      NOT NULL CHECK (cantidad > 0),
    motivo_detalle VARCHAR(500)
);

CREATE INDEX idx_caso_linea_caso ON caso_linea (caso_id);

CREATE TABLE saga_cancelacion (
    id                 UUID         PRIMARY KEY,
    caso_id            UUID         NOT NULL UNIQUE REFERENCES caso_postventa (id),
    pedido_id          VARCHAR(64)  NOT NULL,
    estado             VARCHAR(30)  NOT NULL,
    paso_pendiente     VARCHAR(30)  NOT NULL,
    intentos           INTEGER      NOT NULL DEFAULT 0,
    proximo_intento_en TIMESTAMPTZ,
    ultimo_error       VARCHAR(1000),
    creado_en          TIMESTAMPTZ  NOT NULL,
    actualizado_en     TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_saga_pedido ON saga_cancelacion (pedido_id);

CREATE INDEX idx_saga_reintentos
    ON saga_cancelacion (proximo_intento_en)
    WHERE estado = 'PENDIENTE_REINTENTO';

CREATE UNIQUE INDEX uq_saga_activa_por_pedido
    ON saga_cancelacion (pedido_id)
    WHERE estado IN ('INICIADA', 'PEDIDO_CANCELADO', 'PENDIENTE_REINTENTO', 'REQUIERE_INTERVENCION');
