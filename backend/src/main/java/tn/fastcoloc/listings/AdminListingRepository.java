package tn.fastcoloc.listings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Checked database capabilities; the runtime role never receives blanket administrative table rights. */
@Repository
public class AdminListingRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public AdminListingRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
 @SuppressWarnings("unchecked") private <T>T read(String sql,Object...args){
  String value=jdbc.queryForObject(sql,String.class,args);
  try{return (T)json.readValue(value,Object.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
 }
 public Map<String,Object> list(Map<String,Object> filters){return read("select fastcoloc.admin_listings(?::jsonb)::text",encode(filters));}
 public Map<String,Object> detail(UUID id,boolean lock){return read("select fastcoloc.admin_listing_detail(?,?)::text",id,lock);}
 public List<Map<String,Object>> history(UUID id){return read("select fastcoloc.admin_listing_history(?)::text",id);}
 public void action(UUID id,String action,Object payload,String reason,Long version){
  jdbc.queryForObject("select fastcoloc.admin_listing_action(?,?,?::jsonb,?,?)::text",String.class,id,action,encode(payload),reason,version);
 }
}
