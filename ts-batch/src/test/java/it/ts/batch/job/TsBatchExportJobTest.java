package it.ts.batch.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.ts.batch.TsBatchMain;

/**
 * Esegue il job completo su un database H2 in memoria e verifica il file prodotto.
 */
class TsBatchExportJobTest {

    private static final String JDBC_URL = "jdbc:h2:mem:tsdemo;DB_CLOSE_DELAY=-1";
    private static final long TIMEOUT_MS = 60_000;

    /** Più di due chunk da 500, con l'ultimo parziale. */
    private static final int RECORD_COMPAGNIA_001 = 1234;
    private static final int RECORD_COMPAGNIA_002 = 17;

    private static Connection keepAlive;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void creaDatabase() throws Exception {
        keepAlive = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement st = keepAlive.createStatement()) {
            st.execute("CREATE TABLE DEMO_POLIZZA ("
                    + " ID BIGINT PRIMARY KEY,"
                    + " CODICE_COMPAGNIA VARCHAR(10) NOT NULL,"
                    + " DESCRIZIONE VARCHAR(100),"
                    + " IMPORTO DECIMAL(15,2),"
                    + " DATA_RIFERIMENTO DATE)");
        }
        try (PreparedStatement ps = keepAlive.prepareStatement(
                "INSERT INTO DEMO_POLIZZA VALUES (?, ?, ?, ?, ?)")) {
            long id = 1;
            for (int i = 1; i <= RECORD_COMPAGNIA_001; i++) {
                inserisci(ps, id++, "001", "Polizza " + i, new BigDecimal(i).add(new BigDecimal("0.5")),
                        LocalDate.of(2026, 1, 1).plusDays(i));
            }
            for (int i = 1; i <= RECORD_COMPAGNIA_002; i++) {
                inserisci(ps, id++, "002", "Altra " + i, BigDecimal.TEN, LocalDate.of(2025, 12, 31));
            }
            // valori particolari: separatore nel testo e campi nulli
            inserisci(ps, id++, "003", "Rossi; Mario", new BigDecimal("1500"), LocalDate.of(2026, 2, 5));
            inserisci(ps, id, "003", null, null, null);
            ps.executeBatch();
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

    @AfterAll
    static void chiudiDatabase() throws Exception {
        keepAlive.close();
    }

    @Test
    void esportaConIntestazione() throws Exception {
        Path file = tempDir.resolve("export_001.csv");
        LocalDateTime inizio = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        assertEquals(BatchStatus.COMPLETED, esegui("001", file, null));

        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1 + 1 + RECORD_COMPAGNIA_001 + 2, righe.size());
        assertEquals("CODICE_COMPAGNIA;001", righe.get(0));
        assertEquals("DESCRIZIONE;IMPORTO;DATA_RIFERIMENTO", righe.get(1));
        assertEquals("Polizza 1;1,50;02/01/2026", righe.get(2));
        assertEquals("Polizza 500;500,50;16/05/2027", righe.get(501));
        assertEquals("Polizza 1234;1234,50;19/05/2029", righe.get(righe.size() - 3));
        assertEquals("RECORD_SCRITTI;" + RECORD_COMPAGNIA_001, righe.get(righe.size() - 2));

        String footerData = righe.get(righe.size() - 1);
        assertTrue(footerData.matches("DATA_ELABORAZIONE;\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}"), footerData);
        LocalDateTime elaborazione = LocalDateTime.parse(footerData.substring(footerData.indexOf(';') + 1),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        assertFalse(elaborazione.isBefore(inizio), "data elaborazione " + elaborazione + " < " + inizio);
        assertFalse(elaborazione.isAfter(LocalDateTime.now()), "data elaborazione nel futuro");
    }

    @Test
    void esportaSenzaIntestazione() throws Exception {
        Path file = tempDir.resolve("export_002.csv");

        assertEquals(BatchStatus.COMPLETED, esegui("002", file, "false"));

        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1 + RECORD_COMPAGNIA_002 + 2, righe.size());
        assertEquals("CODICE_COMPAGNIA;002", righe.get(0));
        assertEquals("Altra 1;10,00;31/12/2025", righe.get(1));
        assertEquals("RECORD_SCRITTI;" + RECORD_COMPAGNIA_002, righe.get(righe.size() - 2));
    }

    @Test
    void valoriConSeparatoreENulli() throws Exception {
        Path file = tempDir.resolve("export_003.csv");

        assertEquals(BatchStatus.COMPLETED, esegui("003", file, null));

        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("\"Rossi; Mario\";1500,00;05/02/2026", righe.get(2));
        assertEquals(";;", righe.get(3));
        assertEquals("RECORD_SCRITTI;2", righe.get(4));
    }

    @Test
    void nessunRecordProduceSoloHeaderEFooter() throws Exception {
        Path file = tempDir.resolve("sottocartella/export_999.csv");

        assertEquals(BatchStatus.COMPLETED, esegui("999", file, null));

        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(4, righe.size());
        assertEquals("CODICE_COMPAGNIA;999", righe.get(0));
        assertEquals("RECORD_SCRITTI;0", righe.get(2));
    }

    @Test
    void erroreQueryNonScriveIlFooter() throws Exception {
        Path file = tempDir.resolve("export_ko.csv");
        Properties parametri = parametri("001", file, null);
        // la riga 700 ha una data non valida: la lettura fallisce durante il secondo chunk
        parametri.setProperty("query", "SELECT DESCRIZIONE, IMPORTO,"
                + " CASE WHEN ID = 700 THEN 'non-una-data' ELSE CAST(DATA_RIFERIMENTO AS VARCHAR) END"
                + " FROM DEMO_POLIZZA WHERE CODICE_COMPAGNIA = ? ORDER BY ID");

        assertEquals(BatchStatus.FAILED, TsBatchMain.eseguiJob(parametri, TIMEOUT_MS));

        List<String> righe = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(righe.stream().noneMatch(r -> r.startsWith("RECORD_SCRITTI")), "footer non atteso");
        // committati solo i chunk completi prima dell'errore (riga 700)
        assertEquals("Polizza 500;500,50;16/05/2027", righe.get(righe.size() - 1));
    }

    private static BatchStatus esegui(String codiceCompagnia, Path file, String intestazione) throws Exception {
        return TsBatchMain.eseguiJob(parametri(codiceCompagnia, file, intestazione), TIMEOUT_MS);
    }

    private static Properties parametri(String codiceCompagnia, Path file, String intestazione) {
        Properties p = new Properties();
        p.setProperty("codiceCompagnia", codiceCompagnia);
        p.setProperty("fileOutput", file.toString());
        p.setProperty("jdbcUrl", JDBC_URL);
        p.setProperty("jdbcUser", "sa");
        p.setProperty("jdbcPassword", "");
        if (intestazione != null) {
            p.setProperty("intestazione", intestazione);
        }
        return p;
    }
}
