package tn.fastcoloc.api;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class ApiExceptionHandler {
 private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
 @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> api(ApiException e,HttpServletRequest r) { return response(e.status(),e.code(),e.getMessage(),r); }
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
 ResponseEntity<ApiError> invalid(Exception e,HttpServletRequest r) { return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Champs inconnus ou valeurs invalides.",r); }
 @ExceptionHandler({DataAccessException.class,jakarta.persistence.PersistenceException.class})
 ResponseEntity<ApiError> database(Exception e,HttpServletRequest r) {
  Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
  if(cause instanceof java.sql.SQLException sql){
   if("42501".equals(sql.getSQLState())&&sql.getMessage().contains("FC_MESSAGE_RECIPIENT_UNAVAILABLE"))return response(HttpStatus.FORBIDDEN,"MESSAGE_RECIPIENT_UNAVAILABLE","Ce destinataire ne peut pas recevoir de messages actuellement.",r);
   if("42501".equals(sql.getSQLState())&&sql.getMessage().contains("FC_MESSAGE_ACCESS"))return response(HttpStatus.FORBIDDEN,"MESSAGE_PROFILE_REQUIRED","Complète ton profil avant d’envoyer un message.",r);
   if("P0001".equals(sql.getSQLState())&&sql.getMessage().contains("FC_MESSAGE_RATE_LIMIT"))return response(HttpStatus.TOO_MANY_REQUESTS,"MESSAGE_RATE_LIMIT","Trop de messages. Réessaie dans une minute.",r);
   if("42501".equals(sql.getSQLState()))return response(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Opération refusée.",r);
   if("P0002".equals(sql.getSQLState()))return response(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Utilisateur introuvable.",r);
   if("P0001".equals(sql.getSQLState())&&sql.getMessage().contains("FC_INCOMPLETE"))return response(HttpStatus.CONFLICT,"ONBOARDING_INCOMPLETE","Les cinq étapes doivent être valides avant la complétion.",r);
   if("40001".equals(sql.getSQLState()))return response(HttpStatus.CONFLICT,"STALE_LISTING","Cette annonce a changé. Recharge-la avant de modifier.",r);
   if("23505".equals(sql.getSQLState()))return response(HttpStatus.CONFLICT,"CONCURRENT_CHANGE","Conflit de modification.",r);
   if("22023".equals(sql.getSQLState())||"23514".equals(sql.getSQLState())||"22001".equals(sql.getSQLState()))return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Valeurs invalides.",r);
  }
  log.warn("Database operation failed: {}",e.getClass().getSimpleName());return response(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_UNAVAILABLE","Base de données indisponible.",r);
 }
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
 ResponseEntity<ApiError> forbidden(Exception e,HttpServletRequest r){return response(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Accès refusé.",r);}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
 ResponseEntity<ApiError> size(Exception e,HttpServletRequest r){return response(HttpStatus.PAYLOAD_TOO_LARGE,"PHOTO_SIZE","Photo trop volumineuse.",r);}
 @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected(Exception e,HttpServletRequest r) { log.error("Unexpected API failure: {}",e.getClass().getSimpleName()); return response(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Erreur interne.",r); }
 private ResponseEntity<ApiError> response(HttpStatus s,String c,String m,HttpServletRequest r) { return ResponseEntity.status(s).body(ApiError.of(s.value(),c,m,r.getRequestURI())); }
}
