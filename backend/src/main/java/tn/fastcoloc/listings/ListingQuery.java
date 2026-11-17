package tn.fastcoloc.listings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import tn.fastcoloc.api.ApiException;

public record ListingQuery(String q,String kind,Long governorateId,Long localityId,
 BigDecimal minRent,BigDecimal maxRent,Integer minCapacity,String propertyType,
 String roommateType,List<String> equipment,LocalDate availableOn,
 Double south,Double west,Double north,Double east,String sort,Integer page,Integer size) {
 public ListingQuery {
  page=page==null?0:page;size=size==null?12:size;sort=sort==null?"NEWEST":sort;
 }
 public void validate(){
  if(page<0||page>1000||size<1||size>50||!List.of("NEWEST","RENT_ASC","RENT_DESC").contains(sort)
    ||kind!=null&&!List.of("OFFER","REQUEST").contains(kind)
    ||q!=null&&q.length()>160||propertyType!=null&&propertyType.length()>80||roommateType!=null&&roommateType.length()>80
    ||minRent!=null&&minRent.signum()<0||maxRent!=null&&maxRent.signum()<0
    ||minRent!=null&&maxRent!=null&&minRent.compareTo(maxRent)>0
    ||minCapacity!=null&&(minCapacity<1||minCapacity>50)
    ||equipment!=null&&(equipment.size()>30||equipment.stream().anyMatch(s->s.isBlank()||s.length()>80))) throw invalid();
  int bounds=(south==null?0:1)+(west==null?0:1)+(north==null?0:1)+(east==null?0:1);
  if(bounds!=0&&(bounds!=4||!Double.isFinite(south)||!Double.isFinite(west)||!Double.isFinite(north)||!Double.isFinite(east)
    ||south>north||west>east||south< -90||north>90||west< -180||east>180))throw invalid();
 }
 private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Filtres ou pagination invalides.");}
}
