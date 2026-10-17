-- Apariencia elegida por el usuario (se guarda en la cuenta para verse igual en todos sus aparatos)
ALTER TABLE usuarios ADD COLUMN tema VARCHAR(10) NOT NULL DEFAULT 'OSCURO';
ALTER TABLE usuarios ADD COLUMN acento VARCHAR(10) NOT NULL DEFAULT 'LIMON';
