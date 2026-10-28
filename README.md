# ts-batch

Batch [JBeret](https://github.com/jberet/jsr352) (JSR-352, `javax.batch`) per Java 11 con due job che leggono
il risultato di una query filtrata per codice compagnia:

- `ts-batch-export`: esporta su file CSV;
- `ts-batch-copia-db`: copia i record da un database Oracle a una tabella PostgreSQL.

## Struttura

| Componente | Descrizione |
|---|---|
| `META-INF/batch-jobs/ts-batch-export.xml` | Job `ts-batch-export`, step chunk con `item-count="500"` |
| `DemoQueryItemReader` | Esegue la query demo (stringa, numero, data) con filtro su `CODICE_COMPAGNIA` |
| `CsvFileItemWriter` | Scrive il CSV: riga filtro, intestazione opzionale, dati, footer |
| `META-INF/batch-jobs/ts-batch-copia-db.xml` | Job `ts-batch-copia-db`, step chunk con `item-count="500"` |
| `DbTableItemWriter` | Inserisce i record nella tabella di destinazione, un commit per chunk |
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

## Parametri del job `ts-batch-export`

| Parametro | Obbligatorio | Default |
|---|---|---|
| `codiceCompagnia` | sì | |
| `fileOutput` | sì | |
| `intestazione` | no | `true` |
| `separatore` | no | `;` |
| `encoding` | no | `UTF-8` |
| `dataSourceJndi` oppure `jdbcUrl` / `jdbcUser` / `jdbcPassword` | sì | |
| `query` | no | query demo su `DEMO_POLIZZA` |
| `fetchSize` | no | `500` |

## Job `ts-batch-copia-db` (Oracle → PostgreSQL)

Legge `DEMO_POLIZZA` su Oracle (stessa query e stesso reader del job CSV) e inserisce in `demo_polizza_copia`
su PostgreSQL. DDL di esempio in `src/main/resources/sql/oracle` e `src/main/resources/sql/postgres`.

- Ogni chunk da 500 record è inserito con una batch JDBC e committato; se un chunk fallisce viene annullato.
- Tutte le righe di un'esecuzione hanno la stessa `data_elaborazione`.
- Alla prima esecuzione vengono cancellate le righe già presenti per il codice compagnia (`cancellaEsistenti=true`
  di default), quindi rilanciare il job non duplica i dati.

| Parametro | Obbligatorio | Default |
|---|---|---|
| `codiceCompagnia` | sì | |
| `origineDataSourceJndi` oppure `origineJdbcUrl` / `origineJdbcUser` / `origineJdbcPassword` | sì | |
| `destinazioneDataSourceJndi` oppure `destinazioneJdbcUrl` / `destinazioneJdbcUser` / `destinazioneJdbcPassword` | sì | |
| `query` | no | query demo su `DEMO_POLIZZA` |
| `fetchSize` | no | `500` |
| `insert` | no | insert su `DEMO_POLIZZA_COPIA` (parametri: compagnia, descrizione, importo, data riferimento, data elaborazione) |
| `cancellaEsistenti` | no | `true` |
| `delete` | no | delete su `DEMO_POLIZZA_COPIA` per codice compagnia |

I driver Oracle (`ojdbc11`) e PostgreSQL sono già tra le dipendenze runtime.

## Build ed esecuzione

```bash
mvn clean verify
cd ts-batch/target
java -cp "ts-batch-1.0.0-SNAPSHOT.jar:lib/*" it.ts.batch.TsBatchMain \
    codiceCompagnia=001 fileOutput=/tmp/export_001.csv \
    jdbcUrl=jdbc:oracle:thin:@//host:1521/SERVIZIO jdbcUser=... jdbcPassword=...

java -cp "ts-batch-1.0.0-SNAPSHOT.jar:lib/*" it.ts.batch.TsBatchMain job=ts-batch-copia-db codiceCompagnia=001 \
    origineJdbcUrl=jdbc:oracle:thin:@//host:1521/SERVIZIO origineJdbcUser=... origineJdbcPassword=... \
    destinazioneJdbcUrl=jdbc:postgresql://host:5432/db destinazioneJdbcUser=... destinazioneJdbcPassword=...
```

L'argomento `job` sceglie il job (default `ts-batch-export`).

I test usano H2 (modalità Oracle e PostgreSQL). Per eseguire anche il test su un PostgreSQL reale:

```bash
mvn verify -Dts.it.postgres.url=jdbc:postgresql://localhost:5432/db -Dts.it.postgres.user=... -Dts.it.postgres.password=...
```

Su JDK 17+ aggiungere `--add-opens java.base/java.security=ALL-UNNAMED`.
