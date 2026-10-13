package it.ts.batch.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import it.ts.batch.model.DemoRecord;

class CsvFormatterTest {

    private final CsvFormatter formatter = new CsvFormatter(";");

    @Test
    void rigaFiltro() {
        assertEquals("CODICE_COMPAGNIA;001", formatter.filtro("001"));
    }

    @Test
    void intestazione() {
        assertEquals("DESCRIZIONE;IMPORTO;DATA_RIFERIMENTO", formatter.intestazione());
    }

    @Test
    void datiFormattati() {
        DemoRecord record = new DemoRecord("Polizza RC", new BigDecimal("1234.5"), LocalDate.of(2026, 3, 7));
        assertEquals("Polizza RC;1234,50;07/03/2026", formatter.dati(record));
    }

    @Test
    void importoArrotondatoADueDecimali() {
        assertEquals("10,13", formatter.formatta(new BigDecimal("10.125")));
        assertEquals("-0,50", formatter.formatta(new BigDecimal("-0.5")));
    }

    @Test
    void valoriNulli() {
        assertEquals(";;", formatter.dati(new DemoRecord(null, null, null)));
    }

    @Test
    void escapeValoriConSeparatoreOApici() {
        assertEquals("\"a;b\"", formatter.escape("a;b"));
        assertEquals("\"di \"\"tutto\"\"\"", formatter.escape("di \"tutto\""));
        assertEquals("semplice", formatter.escape("semplice"));
    }

    @Test
    void footer() {
        assertEquals("RECORD_SCRITTI;1200", formatter.footerContatore(1200));
        assertEquals("DATA_ELABORAZIONE;13/10/2026 09:05:03",
                formatter.footerDataElaborazione(LocalDateTime.of(2026, 10, 13, 9, 5, 3)));
    }
}
