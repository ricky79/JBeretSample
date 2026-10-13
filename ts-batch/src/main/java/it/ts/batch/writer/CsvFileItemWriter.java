package it.ts.batch.writer;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.AbstractItemWriter;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import it.ts.batch.model.DemoRecord;

/**
 * Writer che produce il file CSV:
 * <ol>
 *   <li>riga con il valore del filtro (codice compagnia);</li>
 *   <li>riga di intestazione delle colonne (opzionale, property {@code intestazione});</li>
 *   <li>righe dati;</li>
 *   <li>footer: contatore dei record scritti e data/ora di elaborazione.</li>
 * </ol>
 * Il footer viene scritto solo se lo step termina correttamente. Il checkpoint
 * memorizza la posizione nel file e il contatore: in caso di restart il file
 * viene troncato all'ultimo checkpoint e la scrittura riprende da lì.
 */
@Named
@Dependent
public class CsvFileItemWriter extends AbstractItemWriter {

    private static final Logger LOG = Logger.getLogger(CsvFileItemWriter.class.getName());

    static final String FINE_RIGA = "\n";

    @Inject
    private StepContext stepContext;

    @Inject
    @BatchProperty
    private String fileOutput;

    @Inject
    @BatchProperty
    private String codiceCompagnia;

    @Inject
    @BatchProperty
    private String intestazione;

    @Inject
    @BatchProperty
    private String separatore;

    @Inject
    @BatchProperty
    private String encoding;

    private CsvFormatter formatter;
    private FileChannel channel;
    private Writer out;
    private long recordScritti;

    @Override
    public void open(Serializable checkpoint) throws Exception {
        if (fileOutput == null || fileOutput.trim().isEmpty()) {
            throw new IllegalArgumentException("Parametro obbligatorio mancante: fileOutput");
        }
        formatter = new CsvFormatter(isBlank(separatore) ? CsvFormatter.DEFAULT_SEPARATORE : separatore);
        Charset charset = isBlank(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding.trim());

        Path path = Paths.get(fileOutput.trim());
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        out = Channels.newWriter(channel, charset.newEncoder(), -1);

        if (checkpoint == null) {
            channel.truncate(0);
            recordScritti = 0;
            scriviRiga(formatter.filtro(codiceCompagnia));
            if (isIntestazioneAttiva()) {
                scriviRiga(formatter.intestazione());
            }
            out.flush();
            LOG.info(() -> "Creato file " + path.toAbsolutePath());
        } else {
            WriterCheckpoint cp = (WriterCheckpoint) checkpoint;
            channel.truncate(cp.getPosizione());
            channel.position(cp.getPosizione());
            recordScritti = cp.getRecordScritti();
            LOG.info(() -> "Restart su " + path.toAbsolutePath() + " da " + cp);
        }
    }

    @Override
    public void writeItems(List<Object> items) throws Exception {
        for (Object item : items) {
            scriviRiga(formatter.dati((DemoRecord) item));
            recordScritti++;
        }
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        out.flush();
        return new WriterCheckpoint(channel.position(), recordScritti);
    }

    @Override
    public void close() throws Exception {
        if (out == null) {
            return;
        }
        try {
            if (isStepCompletato()) {
                scriviRiga(formatter.footerContatore(recordScritti));
                scriviRiga(formatter.footerDataElaborazione(LocalDateTime.now()));
                LOG.info(() -> "File completato, record scritti: " + recordScritti);
            }
            out.flush();
        } finally {
            out.close();
            out = null;
            channel = null;
        }
    }

    /**
     * JBeret chiama {@code close()} anche in caso di errore o stop: il footer va
     * scritto solo se lo step è ancora in esecuzione senza eccezioni.
     */
    private boolean isStepCompletato() {
        return stepContext.getException() == null
                && stepContext.getBatchStatus() == BatchStatus.STARTED;
    }

    private boolean isIntestazioneAttiva() {
        return isBlank(intestazione) || Boolean.parseBoolean(intestazione.trim());
    }

    private void scriviRiga(String riga) throws IOException {
        out.write(riga);
        out.write(FINE_RIGA);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Stato del writer salvato a ogni checkpoint. */
    static final class WriterCheckpoint implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long posizione;
        private final long recordScritti;

        WriterCheckpoint(long posizione, long recordScritti) {
            this.posizione = posizione;
            this.recordScritti = recordScritti;
        }

        long getPosizione() {
            return posizione;
        }

        long getRecordScritti() {
            return recordScritti;
        }

        @Override
        public String toString() {
            return "WriterCheckpoint[posizione=" + posizione + ", recordScritti=" + recordScritti + "]";
        }
    }
}
