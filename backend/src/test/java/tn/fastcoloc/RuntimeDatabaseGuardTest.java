package tn.fastcoloc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.fastcoloc.security.RuntimeDatabaseGuard;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeDatabaseGuardTest {
    @Test void rejectsUnverifiedTlsBeforeAnyQuery() {
        var jdbc=mock(JdbcTemplate.class);
        var guard=new RuntimeDatabaseGuard(jdbc,"jdbc:postgresql://example.invalid/postgres?sslmode=require",true);
        assertThatThrownBy(()->guard.run(null)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsAnAdministrativeOwnerOrBypassRole() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);
        var guard=new RuntimeDatabaseGuard(jdbc,"jdbc:postgresql://example.invalid/postgres?sslmode=verify-full",true);
        assertThatThrownBy(()->guard.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unsafe database runtime role");
    }
    @Test void acceptsCheckedNonOwnerRoleForLocalPostgres() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(false);
        assertThatCode(()->new RuntimeDatabaseGuard(jdbc,"jdbc:postgresql://localhost/postgres",false).run(null)).doesNotThrowAnyException();
    }
}
