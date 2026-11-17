import java.sql.*;
import java.util.Properties;

/** Diagnostic only. Credentials stay in process environment; never a runtime configuration. */
public class JdbcReadOnlyProbe {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "postgres.nklsbubnyznlfpxpxsrp");
        String password = System.getenv("FASTCOLOC_PROBE_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("FASTCOLOC_PROBE_PASSWORD required");
        properties.setProperty("password", password);
        properties.setProperty("sslmode", "verify-full");
        properties.setProperty("sslrootcert", "config/supabase-prod-ca.crt");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "15");
        properties.setProperty("ApplicationName", "fastcoloc-read-only-probe");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:postgresql://aws-1-eu-north-1.pooler.supabase.com:5432/postgres", properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                query(statement, "select current_database() as database, current_user as role, current_setting('transaction_read_only') as read_only, version() as version");
                query(statement, "select ssl,version,cipher from pg_stat_ssl where pid=pg_backend_pid()");
                query(statement, "select count(*) as tables, count(*) filter(where c.relrowsecurity) as tables_with_rls from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='fastcoloc' and c.relkind='r'");
                query(statement, "select rolname,rolcanlogin,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb from pg_roles where rolname='fastcoloc_backend'");
                query(statement, "select count(*) as fastcoloc_policies from pg_policies where schemaname='fastcoloc'");
                query(statement, "select version,name from supabase_migrations.schema_migrations order by version");
            } finally {
                connection.rollback();
            }
            System.out.println("RESULT=SUCCESS_READ_ONLY_TLS_VERIFY_FULL");
        } catch (SQLException exception) {
            System.err.println("RESULT=FAILED SQLSTATE=" + exception.getSQLState() + " MESSAGE=" + exception.getMessage());
            System.exit(1);
        } finally {
            properties.remove("password");
        }
    }

    private static void query(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData meta = rows.getMetaData();
            int count = 0;
            while (rows.next()) {
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    System.out.print((i == 1 ? "" : " | ") + meta.getColumnLabel(i) + "=" + rows.getString(i));
                }
                System.out.println();
                count++;
            }
            if (count == 0) System.out.println("NO_ROWS");
        }
    }
}
