package it.ts.batch.batchlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.context.JobContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import it.ts.batch.jdbc.ConnessioneJdbc;

/**
 * Batchlet che esegue una query a valore singolo filtrata per codice compagnia
 * e rende disponibile il risultato agli step successivi tramite
 * {@link JobContext#setTransientUserData(Object)}.
 * <p>
 * Il valore è la prima colonna della prima riga; se la query non restituisce
 * righe lo step fallisce. I transient user data non vengono salvati nel
 * repository: lo step va dichiarato con {@code allow-start-if-complete="true"}
 * perché in caso di restart il valore venga ricalcolato.
 */
@Named
@Dependent
public class ValoreFiltroBatchlet extends AbstractBatchlet {

    private static final Logger LOG = Logger.getLogger(ValoreFiltroBatchlet.class.getName());

    /** Query demo: ultimo ID già esportato per la compagnia. */
    public static final String DEFAULT_QUERY =
            "SELECT ULTIMO_ID_ESPORTATO"
            + " FROM DEMO_COMPAGNIA"
            + " WHERE CODICE_COMPAGNIA = ?";

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

    @Override
    public String process() throws Exception {
        if (isBlank(codiceCompagnia)) {
            throw new IllegalArgumentException("Parametro obbligatorio mancante: codiceCompagnia");
        }
        String sql = isBlank(query) ? DEFAULT_QUERY : query;

        Object valore;
        try (Connection connection = ConnessioneJdbc.apri(dataSourceJndi, jdbcUrl, jdbcUser, jdbcPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codiceCompagnia);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Nessun valore filtro trovato per codiceCompagnia=" + codiceCompagnia);
                }
                valore = resultSet.getObject(1);
            }
        }

        jobContext.setTransientUserData(valore);
        LOG.info(() -> "Valore filtro per codiceCompagnia=" + codiceCompagnia + ": " + valore);
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
