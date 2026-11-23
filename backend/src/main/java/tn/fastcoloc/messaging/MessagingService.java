package tn.fastcoloc.messaging;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.onboarding.OnboardingService;
@Service
public class MessagingService {
 private final JdbcTemplate jdbc;private final OnboardingService accounts;
 public MessagingService(JdbcTemplate jdbc,OnboardingService accounts){this.jdbc=jdbc;this.accounts=accounts;}
 private static final String SUMMARY="""
 select c.id,c.listing_id,c.listing_reference,c.listing_title,c.listing_kind,c.locality,c.rent,c.created_at,c.updated_at,
 case when c.owner_id=? then c.guest_name else c.owner_name end as participant_name,
 case when c.owner_id=? then c.guest_id else c.owner_id end as participant_id,
 coalesce((select m.content from fastcoloc.messages m where m.conversation_id=c.id order by m.id desc limit 1),'') as last_message,
 (select count(*) from fastcoloc.messages m where m.conversation_id=c.id and m.sender_id<>? and m.id>case when c.owner_id=? then c.owner_read_id else c.guest_read_id end) as unread_count,
 exists(select 1 from fastcoloc.listings l where l.id=c.listing_id and l.status='PUBLISHED' and not l.is_hidden and l.expires_at>statement_timestamp()) as listing_available
 from fastcoloc.conversations c
 """;
 private List<Map<String,Object>> rows(String sql,Object... args){return jdbc.query(sql,(rs,n)->{var row=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++){Object v=rs.getObject(i);row.put(meta.getColumnLabel(i),v instanceof java.sql.Timestamp t?t.toInstant().toString():v);}return row;},args);}
 private void member(UUID id){if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.conversations where id=?)",Boolean.class,id)))throw new ApiException(HttpStatus.NOT_FOUND,"CONVERSATION_NOT_FOUND","Discussion introuvable.");}
 @Transactional public Map<String,Object> inbox(UUID actor,int page,int size,UUID listingId){accounts.bindAndLock(actor);
 if(listingId!=null)return Map.of("items",rows(SUMMARY+" where c.listing_reference=? and c.guest_id=?",actor,actor,actor,actor,listingId,actor),"page",0,"size",size,"total",jdbc.queryForObject("select count(*) from fastcoloc.conversations where listing_reference=? and guest_id=?",Long.class,listingId,actor));
 return Map.of("items",rows(SUMMARY+" order by c.updated_at desc,c.id limit ? offset ?",actor,actor,actor,actor,size,(long)page*size),"page",page,"size",size,"total",jdbc.queryForObject("select count(*) from fastcoloc.conversations",Long.class));}
 @Transactional public Map<String,Object> detail(UUID actor,UUID id){accounts.bindAndLock(actor);member(id);return rows(SUMMARY+" where c.id=?",actor,actor,actor,actor,id).getFirst();}
 @Transactional public Map<String,Object> start(UUID actor,MessagingController.Start body){accounts.bindAndLock(actor);UUID id=jdbc.queryForObject("select fastcoloc.messaging_start(?,?,?)",UUID.class,body.listingId(),body.content(),body.clientId());return detail(actor,id);}
 @Transactional public Map<String,Object> history(UUID actor,UUID id,Long before,int size){accounts.bindAndLock(actor);member(id);var items=rows("select id,conversation_id,sender_id,client_id,content,created_at from fastcoloc.messages where conversation_id=? and (?::bigint is null or id<?) order by id desc limit ?",id,before,before,size+1);boolean more=items.size()>size;if(more)items.removeLast();Long next=items.isEmpty()?null:((Number)items.getLast().get("id")).longValue();Collections.reverse(items);var result=new LinkedHashMap<String,Object>();result.put("items",items);result.put("hasMore",more);result.put("nextBefore",next);return result;}
 @Transactional public Map<String,Object> send(UUID actor,UUID id,MessagingController.Send body){accounts.bindAndLock(actor);member(id);Long message=jdbc.queryForObject("select fastcoloc.messaging_send(?,?,?)",Long.class,id,body.content(),body.clientId());return rows("select id,conversation_id,sender_id,client_id,content,created_at from fastcoloc.messages where id=?",message).getFirst();}
 @Transactional public void read(UUID actor,UUID id,long throughId){accounts.bindAndLock(actor);member(id);jdbc.queryForObject("select fastcoloc.messaging_read(?,?)",Object.class,id,throughId);}
}
