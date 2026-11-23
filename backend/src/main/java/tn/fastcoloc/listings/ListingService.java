package tn.fastcoloc.listings;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.auth.SupabaseGateway;
import tn.fastcoloc.media.PhotoService;
import tn.fastcoloc.onboarding.OnboardingService;
import tn.fastcoloc.profiles.ProfileDataService;

@Service
public class ListingService {
 private final tn.fastcoloc.geography.LocationService locations;
 private final JdbcTemplate jdbc;private final ProfileDataService data;private final OnboardingService accounts;
 private final SupabaseGateway storage;private final PhotoService images;private final int maxDays;private final int maxPhotos;
 private static final String SELECT="select l.*,p.name as locality,g.name as governorate,g.id as governorate_id from fastcoloc.listings l join fastcoloc.localities p on p.id=l.locality_id join fastcoloc.governorates g on g.id=p.governorate_id ";
 public ListingService(tn.fastcoloc.geography.LocationService locations,JdbcTemplate jdbc,ProfileDataService data,OnboardingService accounts,SupabaseGateway storage,PhotoService images,
  @Value("${fastcoloc.listings.max-publication-days:365}")int maxDays,@Value("${fastcoloc.listings.max-photos:10}")int maxPhotos){this.locations=locations;this.jdbc=jdbc;this.data=data;this.accounts=accounts;this.storage=storage;this.images=images;this.maxDays=maxDays;this.maxPhotos=maxPhotos;}
 private void bind(UUID actor){if(actor==null)jdbc.queryForObject("select set_config('fastcoloc.user_id','',true)",String.class);else accounts.bindAndLock(actor);}
 private void author(UUID actor,UUID id){
  accounts.bindAndLock(actor);
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.listings where id=? and author_id=?)",Boolean.class,id,actor)))throw missing();
  // All updates for this author share the Sprint 1 advisory lock; version prevents stale editors.
 }
 void validate(ListingWrite r){
  if(r.latitude()!=null && r.longitude()!=null)locations.validate(r.latitude().doubleValue(),r.longitude().doubleValue());
  if(r.availableUntil()!=null&&r.availableUntil().isBefore(r.availableFrom()))throw bad("Fin de disponibilité antérieure au début.");
  if(r.publicationDays()>maxDays)throw bad("Durée de publication supérieure à la limite configurée.");
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.localities l join fastcoloc.governorates g on g.id=l.governorate_id where l.id=? and g.country_code='TN')",Boolean.class,r.localityId())))throw bad("Localité tunisienne requise.");
 }
 @Transactional public Map<String,Object> create(UUID actor,ListingWrite r){
  accounts.bindAndLock(actor);validate(r);
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.profiles where id=? and completed_at is not null)",Boolean.class,actor)))throw new ApiException(HttpStatus.CONFLICT,"ONBOARDING_INCOMPLETE","Complète ton profil avant de publier une annonce.");
  UUID id=UUID.randomUUID();var args=values(r);args.addFirst(actor);args.addFirst(id);
  jdbc.update("insert into fastcoloc.listings(id,author_id,kind,title,description,locality_id,street,latitude,longitude,rent,capacity,property_type,roommate_type,equipment,available_from,available_until,publication_days) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?::text[],?,?,?)",args.toArray());
  return detail(actor,id);
 }
 private ArrayList<Object> values(ListingWrite r){return new ArrayList<>(Arrays.asList(r.kind().name(),r.title().trim(),r.description().trim(),r.localityId(),r.street()==null?"":r.street().trim(),r.latitude(),r.longitude(),r.rent(),r.capacity(),r.propertyType().trim(),r.roommateType()==null?"":r.roommateType().trim(),r.equipment().toArray(String[]::new),r.availableFrom(),r.availableUntil(),r.publicationDays()));}
 @Transactional public Map<String,Object> update(UUID actor,UUID id,ListingWrite r){
  author(actor,id);validate(r);if(r.version()==null)throw bad("Version de l'annonce requise.");
  var args=values(r);args.add(id);args.add(actor);args.add(r.version());
  int changed=jdbc.update("update fastcoloc.listings set kind=?,title=?,description=?,locality_id=?,street=?,latitude=?,longitude=?,rent=?,capacity=?,property_type=?,roommate_type=?,equipment=?::text[],available_from=?,available_until=?,publication_days=?,version=version+1,updated_at=now() where id=? and author_id=? and version=?",args.toArray());
  if(changed==0)throw new ApiException(HttpStatus.CONFLICT,"STALE_LISTING","Cette annonce a changé. Recharge-la avant de modifier.");return detail(actor,id);
 }
 @Transactional public Map<String,Object> publish(UUID actor,UUID id,boolean renew){
  author(actor,id);
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.profiles where id=? and completed_at is not null)",Boolean.class,actor)))throw new ApiException(HttpStatus.CONFLICT,"ONBOARDING_INCOMPLETE","Complète ton profil avant de publier ou renouveler.");
  var row=data.rows("select status,publication_days,is_hidden from fastcoloc.listings where id=?",id).getFirst();
  if(Boolean.TRUE.equals(row.get("is_hidden")))throw new ApiException(HttpStatus.FORBIDDEN,"LISTING_HIDDEN","Cette annonce a été masquée par un administrateur.");
  if(!renew&&"PUBLISHED".equals(row.get("status")))throw new ApiException(HttpStatus.CONFLICT,"ALREADY_PUBLISHED","Annonce déjà publiée. Utilise le renouvellement.");
  if(renew&&!"PUBLISHED".equals(row.get("status")))throw bad("Publie d'abord le brouillon.");
  if(((Number)row.get("publication_days")).intValue()>maxDays)throw bad("Durée supérieure à la limite configurée.");
  jdbc.update("update fastcoloc.listings set status='PUBLISHED',published_at=now(),expires_at=now()+publication_days*interval '1 day',updated_at=now(),version=version+1 where id=? and author_id=?",id,actor);
  return detail(actor,id);
 }
 @Transactional public void delete(UUID actor,UUID id){author(actor,id);var paths=jdbc.queryForList("select object_path from fastcoloc.listing_photos where listing_id=?",String.class,id);
  if(!paths.isEmpty())removeObjects(paths);jdbc.update("delete from fastcoloc.listings where id=? and author_id=?",id,actor);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> areas(){return data.rows("select l.id,l.name as locality,g.id as governorate_id,g.name as governorate from fastcoloc.localities l join fastcoloc.governorates g on g.id=l.governorate_id where g.country_code='TN' order by g.name,l.name");}
 @Transactional public Map<String,Object> detail(UUID actor,UUID id){bind(actor);var rows=data.rows(SELECT+"where l.id=?",id);if(rows.isEmpty())throw missing();var row=rows.getFirst();row.put("photos",photos(id));row.put("favorite",actor!=null&&Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.listing_favorites where user_id=? and listing_id=?)",Boolean.class,actor,id)));return row;}
 @Transactional public List<Map<String,Object>> recommendationCandidates(UUID actor){
  bind(actor);
  return data.rows("select l.id,l.title,l.rent,l.property_type,l.capacity,l.equipment,l.available_from,p.name as locality,g.name as governorate,exists(select 1 from fastcoloc.listing_favorites f where f.listing_id=l.id and f.user_id=?) as favorite from fastcoloc.listings l join fastcoloc.localities p on p.id=l.locality_id join fastcoloc.governorates g on g.id=p.governorate_id where l.kind='OFFER' and l.status='PUBLISHED' and not l.is_hidden and l.expires_at>statement_timestamp() and l.author_id<>? order by l.published_at desc,l.id limit 30",actor,actor);
 }
 @Transactional public Map<String,Object> search(UUID actor,ListingQuery q,String mode){
  q.validate();bind(actor);var args=new ArrayList<Object>();var where=new StringBuilder(" where 1=1 ");
  if("mine".equals(mode)){where.append("and l.author_id=? ");args.add(actor);}
  else {where.append("and l.status='PUBLISHED' and not l.is_hidden and l.expires_at>statement_timestamp() ");}
  if("favorites".equals(mode)){where.append("and exists(select 1 from fastcoloc.listing_favorites f where f.listing_id=l.id and f.user_id=?) ");args.add(actor);}
  if("map".equals(mode))where.append("and l.kind='OFFER' ");
  if(q.q()!=null&&!q.q().isBlank()){where.append("and (strpos(lower(l.title||' '||l.description||' '||p.name||' '||g.name),lower(?))>0) ");args.add(q.q().trim());}
  add(where,args,"l.kind=?",q.kind());add(where,args,"g.id=?",q.governorateId());add(where,args,"l.locality_id=?",q.localityId());
  add(where,args,"l.rent>=?",q.minRent());add(where,args,"l.rent<=?",q.maxRent());add(where,args,"l.capacity>=?",q.minCapacity());
  add(where,args,"l.property_type=?",q.propertyType());add(where,args,"l.roommate_type=?",q.roommateType());
  if(q.equipment()!=null&&!q.equipment().isEmpty())add(where,args,"l.equipment @> ?::text[]",q.equipment().toArray(String[]::new));
  if(q.availableOn()!=null){add(where,args,"l.available_from<=?",q.availableOn());add(where,args,"(l.available_until is null or l.available_until>=?)",q.availableOn());}
  if(q.south()!=null){add(where,args,"l.latitude>=?",q.south());add(where,args,"l.latitude<=?",q.north());add(where,args,"l.longitude>=?",q.west());add(where,args,"l.longitude<=?",q.east());}
  String from=" from fastcoloc.listings l join fastcoloc.localities p on p.id=l.locality_id join fastcoloc.governorates g on g.id=p.governorate_id";
  long total=jdbc.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
  String order="mine".equals(mode)?"l.updated_at desc,l.id":switch(q.sort()){case "RENT_ASC"->"l.rent,l.id";case "RENT_DESC"->"l.rent desc,l.id";default->"l.published_at desc,l.id";};
  args.add(q.size());args.add(q.page()*q.size());
  var rows=data.rows("select l.id,l.author_id,l.kind,l.title,l.rent,l.capacity,l.property_type,l.roommate_type,l.latitude,l.longitude,l.status,l.is_hidden,l.published_at,l.expires_at,l.updated_at,l.available_from,l.available_until,l.equipment,p.name as locality,g.name as governorate,g.id as governorate_id"+from+where+" order by "+order+" limit ? offset ?",args.toArray());
  // Map pages contain coordinates only; no Storage signing or gallery N+1 calls.
  if("map".equals(mode))return Map.of("items",rows,"total",total,"page",q.page(),"size",q.size());
  var ids=rows.stream().map(r->(UUID)r.get("id")).toList();var photoRows=new ArrayList<Map<String,Object>>();
  for(UUID id:ids){var first=data.rows("select id,listing_id,object_path,caption,display_order from fastcoloc.listing_photos where listing_id=? order by display_order limit 1",id);photoRows.addAll(first);}
  sign(photoRows);var favorites=actor==null?List.of():jdbc.queryForList("select listing_id from fastcoloc.listing_favorites where user_id=?",UUID.class,actor);
  for(var row:rows){row.put("photos",photoRows.stream().filter(p->p.get("listing_id").equals(row.get("id"))).toList());row.put("favorite",favorites.contains(row.get("id")));}
  return Map.of("items",rows,"total",total,"page",q.page(),"size",q.size());
 }
 private void add(StringBuilder where,List<Object> args,String predicate,Object value){if(value!=null&&!(value instanceof String s&&s.isBlank())){where.append("and ").append(predicate).append(' ');args.add(value);}}
 @Transactional public void favorite(UUID actor,UUID id,boolean add){accounts.bindAndLock(actor);
  if(add){if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from fastcoloc.listings where id=? and status='PUBLISHED' and not is_hidden and expires_at>statement_timestamp())",Boolean.class,id)))throw missing();jdbc.update("insert into fastcoloc.listing_favorites(user_id,listing_id) values(?,?) on conflict do nothing",actor,id);}
  else jdbc.update("delete from fastcoloc.listing_favorites where user_id=? and listing_id=?",actor,id);
 }
 private List<Map<String,Object>> photos(UUID id){var photos=data.rows("select id,listing_id,object_path,caption,mime_type,byte_size,display_order from fastcoloc.listing_photos where listing_id=? order by display_order",id);sign(photos);return photos;}
 void sign(List<Map<String,Object>> photos){
  if(photos.isEmpty())return;var paths=photos.stream().map(p->p.get("object_path").toString()).toList();
  var signed=storage.admin(HttpMethod.POST,"/storage/v1/object/sign/listing-photos",Map.of("expiresIn",300,"paths",paths));
  if(!signed.isArray()||signed.size()!=photos.size())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Photos temporairement indisponibles.");
  for(int i=0;i<photos.size();i++){String url=signed.get(i).path("signedURL").asText(signed.get(i).path("signedUrl").asText());
   if(url.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Photo temporairement indisponible.");
   photos.get(i).put("url",url.startsWith("http")?url:storage.url()+"/storage/v1"+url);photos.get(i).remove("object_path");
  }
 }
 @Transactional public Map<String,Object> upload(UUID actor,UUID listing,MultipartFile file,String caption){
  author(actor,listing);storage.requireServer();if(caption.length()>500)throw bad("Légende trop longue.");
  if(file.isEmpty()||file.getSize()>5242880)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"PHOTO_SIZE","Photo vide ou supérieure à 5 MiB.");
  byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw bad("Photo illisible.");}
  String mime=images.mime(bytes);images.validateImage(bytes);
  if(jdbc.queryForObject("select count(*) from fastcoloc.listing_photos where listing_id=?",Integer.class,listing)>=maxPhotos)throw new ApiException(HttpStatus.CONFLICT,"PHOTO_QUOTA","Quota de photos atteint.");
  UUID photo=UUID.randomUUID();String path=actor+"/"+listing+"/"+photo+(mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":".jpg");
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{removeObjects(List.of(path));}catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(ListingService.class).warn("Listing photo compensation requires retry: {}",photo);}}});
  storage.upload("listing-photos",path,bytes,mime);
  jdbc.update("insert into fastcoloc.listing_photos(id,listing_id,object_path,mime_type,byte_size,caption,display_order) values(?,?,?,?,?,?,(select coalesce(max(display_order),-1)+1 from fastcoloc.listing_photos where listing_id=?))",photo,listing,path,mime,(long)bytes.length,caption,listing);
  return detail(actor,listing);
 }
 @Transactional public void deletePhoto(UUID actor,UUID listing,UUID photo){author(actor,listing);var paths=jdbc.queryForList("select object_path from fastcoloc.listing_photos where listing_id=? and id=?",String.class,listing,photo);if(paths.isEmpty())throw missing();removeObjects(paths);jdbc.update("delete from fastcoloc.listing_photos where id=? and listing_id=?",photo,listing);}
 void removeObjects(List<String> paths){storage.admin(HttpMethod.DELETE,"/storage/v1/object/listing-photos",Map.of("prefixes",paths));}
 private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"LISTING_NOT_FOUND","Annonce introuvable ou accès refusé.");}
 private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_LISTING",message);}
}
