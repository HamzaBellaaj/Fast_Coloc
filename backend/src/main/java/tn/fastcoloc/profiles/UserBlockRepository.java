package tn.fastcoloc.profiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class UserBlockRepository {
    record Member(UUID id, String displayName, boolean listed) {}
    private final JdbcTemplate jdbc;

    UserBlockRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Member member(UUID target) {
        return jdbc.query("select * from fastcoloc.personal_block_member(?)",
            (rs, n) -> new Member(target, rs.getString("display_name"), rs.getBoolean("listed")), target)
            .stream().findFirst().orElse(null);
    }

    List<UserBlockService.BlockedUser> blocked(UUID actor) {
        return jdbc.query("""
            select b.blocked_id, p.display_name, b.created_at
            from fastcoloc.user_blocks b
            cross join lateral fastcoloc.personal_block_member(b.blocked_id) p
            where b.blocker_id=? order by b.created_at desc, b.blocked_id
            """, (rs, n) -> new UserBlockService.BlockedUser(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getObject(3, OffsetDateTime.class)), actor);
    }

    boolean ownsBlock(UUID actor, UUID target) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists (select 1 from fastcoloc.user_blocks where blocker_id=? and blocked_id=?)
            """, Boolean.class, actor, target));
    }

    void add(UUID actor, UUID target) {
        jdbc.update("""
            insert into fastcoloc.user_blocks (blocker_id, blocked_id) values (?, ?)
            on conflict (blocker_id, blocked_id) do nothing
            """, actor, target);
    }

    void remove(UUID actor, UUID target) {
        jdbc.update("delete from fastcoloc.user_blocks where blocker_id=? and blocked_id=?", actor, target);
    }

    void lockPair(UUID actor, UUID target) {
        String first = actor.toString();
        String second = target.toString();
        String pair = first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 1))", rs -> {}, pair);
    }

}
