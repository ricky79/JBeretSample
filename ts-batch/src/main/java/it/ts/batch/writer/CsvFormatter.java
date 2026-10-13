package it.ts.batch.writer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import it.ts.batch.model.DemoRecord;

/**
 * Formattazione delle righe del file CSV: header con il filtro, intestazione
 * colonne, righe dati e footer.
 */
public class CsvFormatter {

    public static final String DEFAULT_SEPARATORE = ";";

    static final String[] INTESTAZIONE = {"DESCRIZIONE", "IMPORTO", "DATA_RIFERIMENTO"};

    /** Date nel formato giorno/mese/anno. */
    static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Data e ora di elaborazione nel footer. */
    static final DateTimeFormatter FORMATO_DATA_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    /** Importi con 2 decimali (arrotondamento HALF_UP), virgola decimale e senza separatore delle migliaia. */
    private static final String PATTERN_IMPORTO = "0.00";

    private final String separatore;
    // DecimalFormat non è thread-safe: un'istanza per formatter (un formatter per writer)
    private final DecimalFormat formatoImporto;

    public CsvFormatter(String separatore) {
        this.separatore = separatore;
        this.formatoImporto = new DecimalFormat(PATTERN_IMPORTO, DecimalFormatSymbols.getInstance(Locale.ITALY));
        this.formatoImporto.setRoundingMode(RoundingMode.HALF_UP);
    }

    public String filtro(String codiceCompagnia) {
        return riga("CODICE_COMPAGNIA", codiceCompagnia);
    }

    public String intestazione() {
        return riga(INTESTAZIONE);
    }

    public String dati(DemoRecord record) {
        return riga(
                record.getDescrizione(),
                formatta(record.getImporto()),
                formatta(record.getDataRiferimento()));
    }

    public String footerContatore(long recordScritti) {
        return riga("RECORD_SCRITTI", Long.toString(recordScritti));
    }

    public String footerDataElaborazione(LocalDateTime dataOra) {
        return riga("DATA_ELABORAZIONE", FORMATO_DATA_ORA.format(dataOra));
    }

    String formatta(BigDecimal importo) {
        return importo == null ? "" : formatoImporto.format(importo);
    }

    String formatta(LocalDate data) {
        return data == null ? "" : FORMATO_DATA.format(data);
    }

    private String riga(String... valori) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valori.length; i++) {
            if (i > 0) {
                sb.append(separatore);
            }
            sb.append(escape(valori[i]));
        }
        return sb.toString();
    }

    /** Racchiude tra doppi apici i valori che contengono separatore, apici o a capo. */
    String escape(String valore) {
        if (valore == null) {
            return "";
        }
        if (valore.contains(separatore) || valore.indexOf('"') >= 0
                || valore.indexOf('\n') >= 0 || valore.indexOf('\r') >= 0) {
            return '"' + valore.replace("\"", "\"\"") + '"';
        }
        return valore;
    }
}
