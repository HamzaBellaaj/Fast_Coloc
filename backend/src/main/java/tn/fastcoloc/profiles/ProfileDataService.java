package tn.fastcoloc.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.security.*;

@Service
public class ProfileDataService {
 private final JdbcTemplate jdbc; private final DatabaseIdentity identity;
 private final AccountAuthorizationService access; private final ProfileService profiles; private final ObjectMapper mapper;
 public ProfileDataService(JdbcTemplate jdbc,DatabaseIdentity identity,AccountAuthorizationService access,ProfileService profiles,ObjectMapper mapper) {
  this.jdbc=jdbc;this.identity=identity;this.access=access;this.profiles=profiles;this.mapper=mapper;
 }
 @Transactional(readOnly=true)
 public Map<String,Object> details(UUID id) {
  identity.bind(id);access.role(id);
  Map<String,Object> result=new LinkedHashMap<>();result.put("profile",profiles.me(id));
  result.put("address",one("select a.*,g.id as governorate_id from fastcoloc.profile_addresses a left join fastcoloc.localities l on l.id=a.locality_id left join fastcoloc.governorates g on g.id=l.governorate_id where a.user_id=?",id));
  result.put("personal",one("select * from fastcoloc.personal_details where user_id=?",id));
  result.put("contacts",one("select * from fastcoloc.contact_details where user_id=?",id));
  result.put("preferences",one("select * from fastcoloc.roommate_preferences where user_id=?",id));
  result.put("preferredLocalities",jdbc.queryForList("select locality_id from fastcoloc.preferred_localities where user_id=? order by locality_id",Long.class,id));
  result.put("photos",rows("select id,caption,mime_type,byte_size,display_order,is_primary,created_at from fastcoloc.profile_photos where user_id=? order by display_order",id));
  return result;
 }
 public List<Map<String,Object>> rows(String sql,Object... args) {
  return jdbc.query(sql,(rs,n)->{
   Map<String,Object> row=new LinkedHashMap<>();var meta=rs.getMetaData();
   for(int i=1;i<=meta.getColumnCount();i++) {
    Object v=rs.getObject(i);
    if(v instanceof java.sql.Array a) {v=a.getArray();a.free();}
    else if(v instanceof java.sql.Timestamp t)v=t.toInstant();
    else if(v instanceof java.sql.Date d)v=d.toLocalDate();
    else if(v!=null && ("jsonb".equals(meta.getColumnTypeName(i))||"json".equals(meta.getColumnTypeName(i)))) {
     try {v=mapper.readTree(rs.getString(i));}catch(java.io.IOException e){throw new SQLException("Invalid stored JSON",e);}
    }
    row.put(meta.getColumnLabel(i),v);
   }return row;
  },args);
 }
 private Map<String,Object> one(String sql,UUID id) {var rows=rows(sql,id);return rows.isEmpty()?Map.of():rows.getFirst();}
}
