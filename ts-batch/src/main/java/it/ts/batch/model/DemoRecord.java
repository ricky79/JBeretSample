package it.ts.batch.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Riga restituita dalla query demo: una stringa, un numero e una data.
 */
public class DemoRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String descrizione;
    private final BigDecimal importo;
    private final LocalDate dataRiferimento;

    public DemoRecord(String descrizione, BigDecimal importo, LocalDate dataRiferimento) {
        this.descrizione = descrizione;
        this.importo = importo;
        this.dataRiferimento = dataRiferimento;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public BigDecimal getImporto() {
        return importo;
    }

    public LocalDate getDataRiferimento() {
        return dataRiferimento;
    }

    @Override
    public String toString() {
        return "DemoRecord[descrizione=" + descrizione + ", importo=" + importo
                + ", dataRiferimento=" + dataRiferimento + "]";
    }
}
