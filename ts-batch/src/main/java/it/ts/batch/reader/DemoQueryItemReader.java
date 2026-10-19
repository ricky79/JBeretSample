package it.ts.batch.reader;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.AbstractItemReader;
import javax.batch.runtime.context.JobContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import it.ts.batch.jdbc.ConnessioneJdbc;
import it.ts.batch.model.DemoRecord;

/**
 * Reader che esegue la query demo filtrata per codice compagnia e restituisce
 * un {@link DemoRecord} per ogni riga.
 * <p>
 * La query ha due parametri: il codice compagnia e il valore filtro calcolato
 * dallo step precedente ({@link it.ts.batch.batchlet.ValoreFiltroBatchlet}) e
 * letto da {@link JobContext#getTransientUserData()}.
 * <p>
 * La connessione si ottiene tramite {@link ConnessioneJdbc}.
 * <p>
 * Il checkpoint è il numero di righe già lette: in caso di restart le righe
 * già elaborate vengono saltate (la query deve avere un ORDER BY stabile).
 */
@Named
@Dependent
public class DemoQueryItemReader extends AbstractItemReader {

    private static final Logger LOG = Logger.getLogger(DemoQueryItemReader.class.getName());

    /** Query demo: stringa, numero, data con filtro sul codice compagnia e sull'ultimo ID esportato. */
    public static final String DEFAULT_QUERY =
            "SELECT DESCRIZIONE, IMPORTO, DATA_RIFERIMENTO"
            + " FROM DEMO_POLIZZA"
            + " WHERE CODICE_COMPAGNIA = ?"
            + " AND ID > ?"
            + " ORDER BY ID";

    @Inject
    private JobContext jobContext;

    @Inject
    @BatchProperty
    private String codiceCompagnia;

    @Inject
    @BatchProperty
    private String query;

    @Inject
    @BatchProperty
    private String dataSourceJndi;

    @Inject
    @BatchProperty
    private String jdbcUrl;

    @Inject
    @BatchProperty
    private String jdbcUser;

    @Inject
    @BatchProperty
    private String jdbcPassword;

    @Inject
    @BatchProperty
    private String fetchSize;

    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private int righeLette;

    @Override
    public void open(Serializable checkpoint) throws Exception {
        if (isBlank(codiceCompagnia)) {
            throw new IllegalArgumentException("Parametro obbligatorio mancante: codiceCompagnia");
        }
        String sql = isBlank(query) ? DEFAULT_QUERY : query;

        Object valoreFiltro = jobContext.getTransientUserData();

        connection = ConnessioneJdbc.apri(dataSourceJndi, jdbcUrl, jdbcUser, jdbcPassword);
        statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        if (!isBlank(fetchSize)) {
            statement.setFetchSize(Integer.parseInt(fetchSize.trim()));
        }
        statement.setString(1, codiceCompagnia);
        statement.setObject(2, valoreFiltro);
        resultSet = statement.executeQuery();

        righeLette = 0;
        int daSaltare = checkpoint == null ? 0 : (Integer) checkpoint;
        while (righeLette < daSaltare && resultSet.next()) {
            righeLette++;
        }
        LOG.info(() -> "Query aperta per codiceCompagnia=" + codiceCompagnia + ", valoreFiltro=" + valoreFiltro
                + (daSaltare > 0 ? ", restart dalla riga " + daSaltare : ""));
    }

    @Override
    public Object readItem() throws Exception {
        if (!resultSet.next()) {
            return null;
        }
        righeLette++;
        Date data = resultSet.getDate(3);
        return new DemoRecord(
                resultSet.getString(1),
                resultSet.getBigDecimal(2),
                data == null ? null : data.toLocalDate());
    }

    @Override
    public Serializable checkpointInfo() {
        return righeLette;
    }

    @Override
    public void close() throws Exception {
        SQLException errore = null;
        for (AutoCloseable risorsa : new AutoCloseable[] {resultSet, statement, connection}) {
            if (risorsa == null) {
                continue;
            }
            try {
                risorsa.close();
            } catch (SQLException e) {
                if (errore == null) {
                    errore = e;
                } else {
                    errore.addSuppressed(e);
                }
            }
        }
        resultSet = null;
        statement = null;
        connection = null;
        if (errore != null) {
            throw errore;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
