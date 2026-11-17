package tn.fastcoloc.security;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
@Component
public class DatabaseIdentity {
 private final JdbcTemplate jdbc;
 public DatabaseIdentity(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 /** Transaction-local identity; automatically cleared on commit/rollback and safe with pooled connections. */
 public void bind(UUID id) {
  if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Identity requires a transaction");
  jdbc.queryForObject("select set_config('fastcoloc.user_id', ?, true)",String.class,id.toString());
 }
}
