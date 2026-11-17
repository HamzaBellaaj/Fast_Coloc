package tn.fastcoloc.profiles;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.security.AccountAuthorizationService;
import tn.fastcoloc.security.DatabaseIdentity;

@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final ProfileRepository profiles;
    private final JdbcTemplate jdbc;
    private final DatabaseIdentity identity;
    private final AccountAuthorizationService access;

    public ProfileService(ProfileRepository profiles, JdbcTemplate jdbc, DatabaseIdentity identity,
                          AccountAuthorizationService access) {
        this.profiles = profiles; this.jdbc = jdbc; this.identity = identity; this.access = access;
    }

    @Transactional(readOnly=true)
    public ProfileResponse me(UUID id) {
        identity.bind(id);
        String role = access.role(id);
        if ("PENDING".equals(role)) throw new ApiException(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND","Synchronisez le profil avant de continuer.");
        return response(id, role);
    }

    /** Called only after a signed token AND confirmed Auth identity have been verified by the filter. */
    @Transactional
    public ProfileResponse sync(UUID id, SyncRequest request) {
        identity.bind(id);
        // Serialize simultaneous first logins from Web/mobile. Lock released on transaction completion.
        jdbc.execute(connection -> {
            var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))");
            statement.setString(1, id.toString());
            return statement;
        }, (org.springframework.jdbc.core.PreparedStatementCallback<Void>) statement -> { statement.execute(); return null; });
        access.role(id); // Never reactivate a blocked account, including a concurrent block before the lock.
        int created = jdbc.update("""
                insert into fastcoloc.profiles(id,first_name,last_name,gender,age)
                values (?,?,?,?,?) on conflict (id) do nothing
                """, id, request.firstName(), request.lastName(), request.gender(), request.age());
        jdbc.update("insert into fastcoloc.account_access(user_id) values (?) on conflict (user_id) do nothing",id);
        for (String table : new String[]{"profile_addresses", "personal_details", "contact_details", "roommate_preferences"}) {
            // Closed list of identifiers; no caller-controlled SQL identifier.
            jdbc.update("insert into fastcoloc."+table+"(user_id) values (?) on conflict (user_id) do nothing",id);
        }
        if (created == 1) log.info("Profile provisioned after verified authentication");
        // Existing names, role, status, completed_at and version are never overwritten by sync.
        return response(id,access.role(id));
    }

    private ProfileResponse response(UUID id, String role) {
        var p=profiles.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND","Profil introuvable."));
        var steps=jdbc.query("select step_number from fastcoloc.onboarding_steps where user_id=? order by step_number",
                (rs,n)->rs.getInt(1),id);
        return ProfileResponse.of(p,role,steps);
    }
}
