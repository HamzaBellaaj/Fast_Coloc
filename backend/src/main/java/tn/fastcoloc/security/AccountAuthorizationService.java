package tn.fastcoloc.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;

@Service
public class AccountAuthorizationService {
    private final DatabaseIdentity identity;
    private final JdbcTemplate jdbc;

    public AccountAuthorizationService(DatabaseIdentity identity, JdbcTemplate jdbc) {
        this.identity = identity;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public String role(UUID id) {
        identity.bind(id);
        var rows = jdbc.query(
                "select app_role,status from fastcoloc.account_access where user_id=?",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, id);
        if (rows.isEmpty()) return "PENDING";
        var row = rows.getFirst();
        if ("BLOCKED".equals(row[1])) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "Compte suspendu.");
        }
        if (!"ACTIVE".equals(row[1]) || !("USER".equals(row[0]) || "ADMIN".equals(row[0]))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accès refusé.");
        }
        return row[0];
    }
}
