# ts-batch

Batch [JBeret](https://github.com/jberet/jsr352) (JSR-352, `javax.batch`) per Java 11 che esporta su file CSV
il risultato di una query filtrata per codice compagnia.

## Struttura

| Componente | Descrizione |
|---|---|
| `META-INF/batch-jobs/ts-batch-export.xml` | Job `ts-batch-export`: step batchlet `recuperaValoreFiltro`, poi step chunk `esportaCsv` con `item-count="500"` |
| `ValoreFiltroBatchlet` | Esegue la query del valore filtro (demo: `ULTIMO_ID_ESPORTATO` da `DEMO_COMPAGNIA`) e lo passa al reader tramite i transient user data del `JobContext` |
| `DemoQueryItemReader` | Esegue la query demo (stringa, numero, data) con filtro su `CODICE_COMPAGNIA` e `ID > valore filtro` |
| `ConnessioneJdbc` | Apertura della connessione (DataSource JNDI o `DriverManager`) |
| `CsvFileItemWriter` | Scrive il CSV: riga filtro, intestazione opzionale, dati, footer |
| `CsvFormatter` | Formattazione: date `dd/MM/yyyy`, importi `0,00`, footer `dd/MM/yyyy HH:mm:ss` |
| `TsBatchMain` | Avvio da riga di comando |

## Formato del file

```
CODICE_COMPAGNIA;001
DESCRIZIONE;IMPORTO;DATA_RIFERIMENTO        <- solo se intestazione=true (default)
Polizza 1;1,50;02/01/2026
...
RECORD_SCRITTI;1234
DATA_ELABORAZIONE;13/10/2026 09:05:03
```

Il footer viene scritto solo se lo step termina correttamente.

Se la query del valore filtro non restituisce righe il job termina `FAILED` senza creare il file.

## Parametri del job

| Parametro | Obbligatorio | Default |
|---|---|---|
| `codiceCompagnia` | sì | |
| `fileOutput` | sì | |
| `intestazione` | no | `true` |
| `separatore` | no | `;` |
| `encoding` | no | `UTF-8` |
| `dataSourceJndi` oppure `jdbcUrl` / `jdbcUser` / `jdbcPassword` | sì | |
| `queryValoreFiltro` | no | query demo su `DEMO_COMPAGNIA`; un parametro (codice compagnia), valore dalla prima colonna della prima riga |
| `query` | no | query demo su `DEMO_POLIZZA`; due parametri (codice compagnia, valore filtro) |
| `fetchSize` | no | `500` |

## Build ed esecuzione

```bash
mvn clean verify
cd ts-batch/target
java -cp "ts-batch-1.0.0-SNAPSHOT.jar:lib/*:/path/driver-jdbc.jar" it.ts.batch.TsBatchMain \
    codiceCompagnia=001 fileOutput=/tmp/export_001.csv \
    jdbcUrl=jdbc:... jdbcUser=... jdbcPassword=...
```

Su JDK 17+ aggiungere `--add-opens java.base/java.security=ALL-UNNAMED`.
