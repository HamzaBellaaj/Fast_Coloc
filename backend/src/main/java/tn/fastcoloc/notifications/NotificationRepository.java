package tn.fastcoloc.notifications;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class NotificationRepository {
 public record Delivery(UUID eventId,String email,UUID conversation,UUID lease){}
 private final JdbcTemplate jdbc;
 public NotificationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public List<Delivery> claim(){return jdbc.query("select * from fastcoloc.notification_claim(5)",(r,n)->new Delivery(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getObject(4,UUID.class)));}
 @Transactional public boolean ready(Delivery d){return Boolean.TRUE.equals(jdbc.queryForObject("select fastcoloc.notification_ready(?,?)",Boolean.class,d.eventId(),d.lease()));}
 @Transactional public void finish(Delivery d,String outcome,String code){jdbc.queryForObject("select fastcoloc.notification_finish(?,?,?,?)",Object.class,d.eventId(),d.lease(),outcome,code);}
}
