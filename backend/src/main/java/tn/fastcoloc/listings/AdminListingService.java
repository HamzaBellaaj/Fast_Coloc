package tn.fastcoloc.listings;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.security.DatabaseIdentity;

@Service
public class AdminListingService {
 private final AdminListingRepository repo;private final DatabaseIdentity identity;private final ListingService listings;
 public AdminListingService(AdminListingRepository repo,DatabaseIdentity identity,ListingService listings){this.repo=repo;this.identity=identity;this.listings=listings;}
 @Transactional public Map<String,Object> list(UUID actor,Map<String,Object> filters){identity.bind(actor);return repo.list(filters);}
 @Transactional public Map<String,Object> detail(UUID actor,UUID id){identity.bind(actor);return signed(repo.detail(id,false));}
 @Transactional public List<Map<String,Object>> history(UUID actor,UUID id){identity.bind(actor);return repo.history(id);}
 @Transactional public Map<String,Object> edit(UUID actor,UUID id,ListingWrite body,String reason){
  identity.bind(actor);repo.detail(id,true);listings.validate(body);
  repo.action(id,"EDIT",body,reason,body.version());return signed(repo.detail(id,false));
 }
 @Transactional public Map<String,Object> moderate(UUID actor,UUID id,String action,String reason,Long version){
  identity.bind(actor);var before=repo.detail(id,true);
  if(version==null||((Number)before.get("version")).longValue()!=version)throw new ApiException(HttpStatus.CONFLICT,"STALE_LISTING","Recharge cette annonce avant de confirmer.");
  if("DELETE".equals(action)){
   var paths=photos(before).stream().map(p->p.get("object_path").toString()).toList();
   if(!paths.isEmpty())listings.removeObjects(paths);
  }
  repo.action(id,action,Map.of(),reason,version);
  return "DELETE".equals(action)?Map.of():signed(repo.detail(id,false));
 }
 @SuppressWarnings("unchecked") private List<Map<String,Object>> photos(Map<String,Object> row){return (List<Map<String,Object>>)row.get("photos");}
 private Map<String,Object> signed(Map<String,Object> row){listings.sign(photos(row));return row;}
}
