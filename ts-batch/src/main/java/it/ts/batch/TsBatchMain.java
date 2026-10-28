package it.ts.batch;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;

/**
 * Avvio da riga di comando del job di export.
 * <p>
 * L'argomento opzionale {@code job} sceglie il job da eseguire (default {@value #JOB_NAME});
 * gli altri argomenti {@code chiave=valore} sono i parametri del job.
 * <p>
 * Esempi:
 * <pre>
 * java -cp "ts-batch.jar:lib/*" it.ts.batch.TsBatchMain \
 *     codiceCompagnia=001 fileOutput=/tmp/export.csv jdbcUrl=jdbc:... jdbcUser=... jdbcPassword=...
 *
 * java -cp "ts-batch.jar:lib/*" it.ts.batch.TsBatchMain job=ts-batch-copia-db codiceCompagnia=001 \
 *     origineJdbcUrl=jdbc:oracle:thin:@//host:1521/SERVIZIO origineJdbcUser=... origineJdbcPassword=... \
 *     destinazioneJdbcUrl=jdbc:postgresql://host:5432/db destinazioneJdbcUser=... destinazioneJdbcPassword=...
 * </pre>
 * Su JDK 17+ aggiungere {@code --add-opens java.base/java.security=ALL-UNNAMED}
 * (richiesto da wildfly-security-manager, dipendenza di JBeret).
 * <p>
 * Exit code 0 se il job termina COMPLETED, 1 altrimenti, 2 per argomenti non validi.
 */
public final class TsBatchMain {

    public static final String JOB_NAME = "ts-batch-export";
    public static final String JOB_COPIA_DB = "ts-batch-copia-db";

    private static final String ARG_JOB = "job";

    private static final Set<BatchStatus> STATI_FINALI =
            EnumSet.of(BatchStatus.COMPLETED, BatchStatus.FAILED, BatchStatus.STOPPED, BatchStatus.ABANDONED);

    private TsBatchMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        Properties parametri = new Properties();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (idx <= 0) {
                System.err.println("Argomento non valido (atteso chiave=valore): " + arg);
                System.exit(2);
            }
            parametri.setProperty(arg.substring(0, idx), arg.substring(idx + 1));
        }

        String jobName = (String) parametri.remove(ARG_JOB);
        if (jobName == null || jobName.trim().isEmpty()) {
            jobName = JOB_NAME;
        }

        BatchStatus esito = eseguiJob(jobName.trim(), parametri, 0);
        System.out.println("Job " + jobName + " terminato con stato " + esito);
        System.exit(esito == BatchStatus.COMPLETED ? 0 : 1);
    }

    /**
     * Avvia il job {@value #JOB_NAME} e attende la fine.
     *
     * @param timeoutMillis tempo massimo di attesa, 0 per attendere senza limiti
     * @return lo stato finale del job
     */
    public static BatchStatus eseguiJob(Properties parametri, long timeoutMillis) throws InterruptedException {
        return eseguiJob(JOB_NAME, parametri, timeoutMillis);
    }

    /**
     * Avvia il job indicato e attende la fine.
     *
     * @param timeoutMillis tempo massimo di attesa, 0 per attendere senza limiti
     * @return lo stato finale del job
     */
    public static BatchStatus eseguiJob(String jobName, Properties parametri, long timeoutMillis)
            throws InterruptedException {
        JobOperator operator = BatchRuntime.getJobOperator();
        long executionId = operator.start(jobName, parametri);
        long scadenza = timeoutMillis > 0 ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis) : 0;

        JobExecution execution = operator.getJobExecution(executionId);
        while (!STATI_FINALI.contains(execution.getBatchStatus())) {
            if (scadenza != 0 && System.nanoTime() > scadenza) {
                throw new IllegalStateException("Timeout in attesa della fine del job " + executionId);
            }
            TimeUnit.MILLISECONDS.sleep(100);
            execution = operator.getJobExecution(executionId);
        }
        return execution.getBatchStatus();
    }
}
