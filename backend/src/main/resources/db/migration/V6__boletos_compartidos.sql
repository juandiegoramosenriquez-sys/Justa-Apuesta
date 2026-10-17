-- Boletos compartidos por link (foto de los picks al momento de compartir)
CREATE TABLE boletos_compartidos (
    codigo      VARCHAR(12) NOT NULL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    json_boleto LONGTEXT    NOT NULL,
    creado_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_boleto_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);
