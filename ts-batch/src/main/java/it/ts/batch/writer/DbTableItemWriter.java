package it.ts.batch.writer;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.AbstractItemWriter;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import it.ts.batch.model.DemoRecord;

/**
 * Writer che inserisce i {@link DemoRecord} in una tabella di un database di
 * destinazione (PostgreSQL nel job {@code ts-batch-copia-db}).
 * <p>
 * Ogni chunk viene inserito con un'unica batch JDBC e committato alla fine di
 * {@link #writeItems(List)}; in caso di errore il chunk viene annullato.
 * <p>
 * Alla prima esecuzione (non in restart), se {@code cancellaEsistenti} è
 * {@code true} (default), vengono cancellate le righe già presenti per il codice
 * compagnia: rilanciare il job non duplica i dati.
 * <p>
 * La query di insert ({@code insert}) deve avere i parametri nell'ordine:
 * codice compagnia, descrizione, importo, data riferimento, data elaborazione.
 */
@Named
@Dependent
public class DbTableItemWriter extends AbstractItemWriter {

    private static final Logger LOG = Logger.getLogger(DbTableItemWriter.class.getName());

    public static final String DEFAULT_INSERT =
            "INSERT INTO DEMO_POLIZZA_COPIA"
            + " (CODICE_COMPAGNIA, DESCRIZIONE, IMPORTO, DATA_RIFERIMENTO, DATA_ELABORAZIONE)"
            + " VALUES (?, ?, ?, ?, ?)";

    public static final String DEFAULT_DELETE =
            "DELETE FROM DEMO_POLIZZA_COPIA WHERE CODICE_COMPAGNIA = ?";

    @Inject
    @BatchProperty
    private String codiceCompagnia;

    @Inject
    @BatchProperty
    private String insert;

    @Inject
    @BatchProperty
    private String delete;

    @Inject
    @BatchProperty
    private String cancellaEsistenti;

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

    private Connection connection;
    private PreparedStatement statement;
    private long recordScritti;
    private LocalDateTime dataElaborazione;

    @Override
    public void open(Serializable checkpoint) throws Exception {
        if (isBlank(codiceCompagnia)) {
            throw new IllegalArgumentException("Parametro obbligatorio mancante: codiceCompagnia");
        }
        connection = openConnection();
        connection.setAutoCommit(false);

        if (checkpoint == null) {
            recordScritti = 0;
            dataElaborazione = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            if (isBlank(cancellaEsistenti) || Boolean.parseBoolean(cancellaEsistenti.trim())) {
                cancellaRigheEsistenti();
            }
        } else {
            WriterCheckpoint cp = (WriterCheckpoint) checkpoint;
            recordScritti = cp.getRecordScritti();
            dataElaborazione = cp.getDataElaborazione();
            LOG.info(() -> "Restart da " + cp);
        }
        statement = connection.prepareStatement(isBlank(insert) ? DEFAULT_INSERT : insert);
    }

    @Override
    public void writeItems(List<Object> items) throws Exception {
        try {
            Timestamp elaborazione = Timestamp.valueOf(dataElaborazione);
            for (Object item : items) {
                DemoRecord record = (DemoRecord) item;
                statement.setString(1, codiceCompagnia);
                statement.setString(2, record.getDescrizione());
                statement.setBigDecimal(3, record.getImporto());
                if (record.getDataRiferimento() == null) {
                    statement.setNull(4, Types.DATE);
                } else {
                    statement.setDate(4, Date.valueOf(record.getDataRiferimento()));
                }
                statement.setTimestamp(5, elaborazione);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
            recordScritti += items.size();
        } catch (Exception e) {
            rollback(e);
            throw e;
        }
    }

    @Override
    public Serializable checkpointInfo() {
        return new WriterCheckpoint(recordScritti, dataElaborazione);
    }

    @Override
    public void close() throws Exception {
        if (connection == null) {
            return;
        }
        try {
            if (statement != null) {
                statement.close();
            }
        } finally {
            // eventuali modifiche non committate vengono annullate
            connection.rollback();
            connection.close();
            statement = null;
            connection = null;
            LOG.info(() -> "Record inseriti per codiceCompagnia=" + codiceCompagnia + ": " + recordScritti);
        }
    }

    private void cancellaRigheEsistenti() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(isBlank(delete) ? DEFAULT_DELETE : delete)) {
            ps.setString(1, codiceCompagnia);
            int cancellate = ps.executeUpdate();
            connection.commit();
            LOG.info(() -> "Cancellate " + cancellate + " righe esistenti per codiceCompagnia=" + codiceCompagnia);
        } catch (SQLException e) {
            rollback(e);
            throw e;
        }
    }

    private void rollback(Exception causa) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            causa.addSuppressed(e);
        }
    }

    private Connection openConnection() throws SQLException, NamingException {
        if (!isBlank(dataSourceJndi)) {
            DataSource ds = (DataSource) new InitialContext().lookup(dataSourceJndi);
            return ds.getConnection();
        }
        if (isBlank(jdbcUrl)) {
            throw new IllegalArgumentException("Specificare dataSourceJndi oppure jdbcUrl del database di destinazione");
        }
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Stato del writer salvato a ogni checkpoint. */
    static final class WriterCheckpoint implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long recordScritti;
        private final LocalDateTime dataElaborazione;

        WriterCheckpoint(long recordScritti, LocalDateTime dataElaborazione) {
            this.recordScritti = recordScritti;
            this.dataElaborazione = dataElaborazione;
        }

        long getRecordScritti() {
            return recordScritti;
        }

        LocalDateTime getDataElaborazione() {
            return dataElaborazione;
        }

        @Override
        public String toString() {
            return "WriterCheckpoint[recordScritti=" + recordScritti + ", dataElaborazione=" + dataElaborazione + "]";
        }
    }
}
