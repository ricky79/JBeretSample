package it.ts.batch.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import it.ts.batch.TsBatchMain;

/**
 * Esegue il job ts-batch-copia-db: origine H2 in modalità Oracle, destinazione H2
 * in modalità PostgreSQL, entrambe create con i DDL del progetto.
 * <p>
 * Con {@code -Dts.it.postgres.url=jdbc:postgresql://...} (più {@code ts.it.postgres.user}
 * e {@code ts.it.postgres.password}) viene eseguito anche un test su un PostgreSQL reale.
 */
class TsBatchCopiaDbJobTest {

    private static final String ORIGINE_URL = "jdbc:h2:mem:origine_oracle;MODE=Oracle;DB_CLOSE_DELAY=-1";
    private static final String DESTINAZIONE_URL =
            "jdbc:h2:mem:destinazione_pg;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final long TIMEOUT_MS = 60_000;

    private static final int RECORD_COMPAGNIA_001 = 1234;

    private static Connection origine;
    private static Connection destinazione;

    @BeforeAll
    static void creaDatabase() throws Exception {
        origine = DriverManager.getConnection(ORIGINE_URL, "sa", "");
        eseguiScript(origine, "/sql/oracle/demo_polizza.sql");
        try (PreparedStatement ps = origine.prepareStatement("INSERT INTO DEMO_POLIZZA VALUES (?, ?, ?, ?, ?)")) {
            long id = 1;
            for (int i = 1; i <= RECORD_COMPAGNIA_001; i++) {
                inserisci(ps, id++, "001", "Polizza " + i, new BigDecimal(i).add(new BigDecimal("0.25")),
                        LocalDate.of(2026, 1, 1).plusDays(i));
            }
            for (int i = 1; i <= 5; i++) {
                inserisci(ps, id++, "002", "Altra " + i, BigDecimal.ONE, LocalDate.of(2025, 12, 31));
            }
            inserisci(ps, id, "003", null, null, null);
            ps.executeBatch();
        }

        destinazione = DriverManager.getConnection(DESTINAZIONE_URL, "sa", "");
        eseguiScript(destinazione, "/sql/postgres/demo_polizza_copia.sql");
    }

    @AfterAll
    static void chiudiDatabase() throws Exception {
        origine.close();
        destinazione.close();
    }

    @BeforeEach
    void svuotaDestinazione() throws Exception {
        try (Statement st = destinazione.createStatement()) {
            st.executeUpdate("DELETE FROM demo_polizza_copia");
        }
    }

    @Test
    void copiaIRecordDellaCompagnia() throws Exception {
        assertEquals(BatchStatus.COMPLETED, esegui(parametri("001", DESTINAZIONE_URL, "sa", "")));

        verificaCopia(destinazione);
        assertEquals(0, conta(destinazione, "002"));
    }

    @Test
    void rilancioNonDuplicaENonToccaAltreCompagnie() throws Exception {
        assertEquals(BatchStatus.COMPLETED, esegui(parametri("002", DESTINAZIONE_URL, "sa", "")));
        assertEquals(BatchStatus.COMPLETED, esegui(parametri("001", DESTINAZIONE_URL, "sa", "")));
        assertEquals(BatchStatus.COMPLETED, esegui(parametri("001", DESTINAZIONE_URL, "sa", "")));

        assertEquals(RECORD_COMPAGNIA_001, conta(destinazione, "001"));
        assertEquals(5, conta(destinazione, "002"));
    }

    @Test
    void senzaCancellazioneAccodaIRecord() throws Exception {
        Properties p = parametri("002", DESTINAZIONE_URL, "sa", "");
        assertEquals(BatchStatus.COMPLETED, esegui(p));
        p.setProperty("cancellaEsistenti", "false");
        assertEquals(BatchStatus.COMPLETED, esegui(p));

        assertEquals(10, conta(destinazione, "002"));
    }

    @Test
    void valoriNulli() throws Exception {
        assertEquals(BatchStatus.COMPLETED, esegui(parametri("003", DESTINAZIONE_URL, "sa", "")));

        try (Statement st = destinazione.createStatement();
             ResultSet rs = st.executeQuery("SELECT descrizione, importo, data_riferimento, data_elaborazione"
                     + " FROM demo_polizza_copia WHERE codice_compagnia = '003'")) {
            rs.next();
            assertNull(rs.getString(1));
            assertNull(rs.getBigDecimal(2));
            assertNull(rs.getDate(3));
            assertNotNull(rs.getTimestamp(4));
        }
    }

    @Test
    void erroreInLetturaLasciaSoloIChunkCommittati() throws Exception {
        Properties p = parametri("001", DESTINAZIONE_URL, "sa", "");
        // la riga 700 ha una data non valida: la lettura fallisce durante il secondo chunk
        p.setProperty("query", "SELECT DESCRIZIONE, IMPORTO,"
                + " CASE WHEN ID = 700 THEN 'non-una-data' ELSE TO_CHAR(DATA_RIFERIMENTO, 'YYYY-MM-DD') END"
                + " FROM DEMO_POLIZZA WHERE CODICE_COMPAGNIA = ? ORDER BY ID");

        assertEquals(BatchStatus.FAILED, esegui(p));

        assertEquals(500, conta(destinazione, "001"));
    }

    @Test
    void erroreInScritturaAnnullaIlChunk() throws Exception {
        Properties p = parametri("001", DESTINAZIONE_URL, "sa", "");
        p.setProperty("insert", "INSERT INTO tabella_inesistente VALUES (?, ?, ?, ?, ?)");

        assertEquals(BatchStatus.FAILED, esegui(p));

        assertEquals(0, conta(destinazione, "001"));
    }

    @Test
    @EnabledIfSystemProperty(named = "ts.it.postgres.url", matches = "jdbc:postgresql:.+")
    void copiaSuPostgresReale() throws Exception {
        String url = System.getProperty("ts.it.postgres.url");
        String user = System.getProperty("ts.it.postgres.user", "postgres");
        String password = System.getProperty("ts.it.postgres.password", "");
        try (Connection pg = DriverManager.getConnection(url, user, password)) {
            try (Statement st = pg.createStatement()) {
                st.execute("DROP TABLE IF EXISTS demo_polizza_copia");
            }
            eseguiScript(pg, "/sql/postgres/demo_polizza_copia.sql");

            assertEquals(BatchStatus.COMPLETED, esegui(parametri("001", url, user, password)));
            assertEquals(BatchStatus.COMPLETED, esegui(parametri("001", url, user, password)));

            verificaCopia(pg);
        }
    }

    private static void verificaCopia(Connection db) throws Exception {
        assertEquals(RECORD_COMPAGNIA_001, conta(db, "001"));
        try (Statement st = db.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT descrizione, importo, data_riferimento"
                    + " FROM demo_polizza_copia WHERE codice_compagnia = '001' ORDER BY id")) {
                rs.next();
                assertEquals("Polizza 1", rs.getString(1));
                assertEquals(new BigDecimal("1.25"), rs.getBigDecimal(2));
                assertEquals(LocalDate.of(2026, 1, 2), rs.getDate(3).toLocalDate());
            }
            // tutte le righe di un'esecuzione hanno la stessa data elaborazione
            try (ResultSet rs = st.executeQuery("SELECT COUNT(DISTINCT data_elaborazione)"
                    + " FROM demo_polizza_copia WHERE codice_compagnia = '001'")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    private static BatchStatus esegui(Properties parametri) throws Exception {
        return TsBatchMain.eseguiJob(TsBatchMain.JOB_COPIA_DB, parametri, TIMEOUT_MS);
    }

    private static Properties parametri(String codiceCompagnia, String destinazioneUrl, String user, String password) {
        Properties p = new Properties();
        p.setProperty("codiceCompagnia", codiceCompagnia);
        p.setProperty("origineJdbcUrl", ORIGINE_URL);
        p.setProperty("origineJdbcUser", "sa");
        p.setProperty("origineJdbcPassword", "");
        p.setProperty("destinazioneJdbcUrl", destinazioneUrl);
        p.setProperty("destinazioneJdbcUser", user);
        p.setProperty("destinazioneJdbcPassword", password);
        return p;
    }

    private static int conta(Connection db, String codiceCompagnia) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT COUNT(*) FROM demo_polizza_copia WHERE codice_compagnia = ?")) {
            ps.setString(1, codiceCompagnia);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void inserisci(PreparedStatement ps, long id, String compagnia, String descrizione,
                                  BigDecimal importo, LocalDate data) throws Exception {
        ps.setLong(1, id);
        ps.setString(2, compagnia);
        ps.setString(3, descrizione);
        ps.setBigDecimal(4, importo);
        ps.setDate(5, data == null ? null : Date.valueOf(data));
        ps.addBatch();
    }

    /** Esegue uno script SQL del classpath, istruzioni separate da ';'. */
    private static void eseguiScript(Connection db, String risorsa) throws Exception {
        String script;
        try (InputStream in = TsBatchCopiaDbJobTest.class.getResourceAsStream(risorsa)) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement st = db.createStatement()) {
            for (String istruzione : script.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!istruzione.isBlank()) {
                    st.execute(istruzione);
                }
            }
        }
    }
}
