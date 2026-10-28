-- Tabella di destinazione (PostgreSQL) scritta dal job ts-batch-copia-db
CREATE TABLE demo_polizza_copia (
    id                BIGSERIAL      PRIMARY KEY,
    codice_compagnia  VARCHAR(10)    NOT NULL,
    descrizione       VARCHAR(100),
    importo           NUMERIC(15, 2),
    data_riferimento  DATE,
    data_elaborazione TIMESTAMP      NOT NULL
);
CREATE INDEX demo_polizza_copia_compagnia_idx ON demo_polizza_copia (codice_compagnia);
