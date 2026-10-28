-- Tabella di origine (Oracle) letta dal job ts-batch-copia-db
CREATE TABLE DEMO_POLIZZA (
    ID               NUMBER(19)     PRIMARY KEY,
    CODICE_COMPAGNIA VARCHAR2(10)   NOT NULL,
    DESCRIZIONE      VARCHAR2(100),
    IMPORTO          NUMBER(15, 2),
    DATA_RIFERIMENTO DATE
);
