package it.ts.batch.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Apertura della connessione condivisa dagli artefatti batch: dal DataSource
 * JNDI ({@code dataSourceJndi}) se valorizzato, altrimenti tramite
 * {@link DriverManager} ({@code jdbcUrl}, {@code jdbcUser}, {@code jdbcPassword}).
 */
public final class ConnessioneJdbc {

    private ConnessioneJdbc() {
    }

    public static Connection apri(String dataSourceJndi, String jdbcUrl, String jdbcUser, String jdbcPassword)
            throws SQLException, NamingException {
        if (!isBlank(dataSourceJndi)) {
            DataSource ds = (DataSource) new InitialContext().lookup(dataSourceJndi);
            return ds.getConnection();
        }
        if (isBlank(jdbcUrl)) {
            throw new IllegalArgumentException("Specificare dataSourceJndi oppure jdbcUrl");
        }
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
