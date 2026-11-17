package tn.fastcoloc.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.*;

@Component
public class RuntimeDatabaseGuard implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(RuntimeDatabaseGuard.class);
    private final JdbcTemplate jdbc;
    private final String url;
    private final boolean requireSsl;
    public RuntimeDatabaseGuard(JdbcTemplate jdbc,@Value("${spring.datasource.url}") String url,
            @Value("${fastcoloc.database.require-ssl:true}") boolean requireSsl) {
        this.jdbc=jdbc; this.url=url; this.requireSsl=requireSsl;
    }
    @Override public void run(ApplicationArguments args) {
        if(requireSsl && !url.matches(".*[?&]sslmode=verify-full(?:&.*)?$"))
            throw new IllegalStateException("Runtime JDBC requires sslmode=verify-full and a trusted CA");
        Boolean unsafe=jdbc.queryForObject("""
            select r.rolsuper or r.rolbypassrls or r.rolcreaterole or r.rolcreatedb or r.rolreplication or r.rolinherit or
                   r.rolname <> 'fastcoloc_backend' or
                   has_database_privilege(current_user,current_database(),'CREATE') or
                   exists(select 1 from pg_namespace n where n.nspname !~ '^pg_temp_'
                          and n.nspname !~ '^pg_toast_temp_' and has_schema_privilege(current_user,n.oid,'CREATE')) or
                   exists(select 1 from pg_auth_members m where m.member=r.oid) or
                   exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
                          where n.nspname='fastcoloc' and c.relowner=r.oid) or
                   exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
                          where n.nspname='fastcoloc' and c.relkind='r' and not c.relrowsecurity)
            from pg_roles r where r.rolname=current_user
            """,Boolean.class);
        if(!Boolean.FALSE.equals(unsafe)) throw new IllegalStateException("Unsafe database runtime role or disabled RLS");
        log.info("JDBC connection verified; non-owner runtime role and RLS checked");
    }
}
