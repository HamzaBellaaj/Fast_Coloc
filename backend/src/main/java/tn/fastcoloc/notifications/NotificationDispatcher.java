package tn.fastcoloc.notifications;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="fastcoloc.notifications.enabled",havingValue="true")
public class NotificationDispatcher {
 private static final Logger log=LoggerFactory.getLogger(NotificationDispatcher.class);
 private final NotificationRepository repository;private final NotificationGateway gateway;
 public NotificationDispatcher(NotificationRepository repository,NotificationGateway gateway){this.repository=repository;this.gateway=gateway;}
 @Scheduled(fixedDelayString="${fastcoloc.notifications.poll-ms:15000}",initialDelayString="${fastcoloc.notifications.initial-delay-ms:30000}")
 public void dispatch(){
  if(!gateway.configured())return;
  try{for(var delivery:repository.claim()){
   try{if(!repository.ready(delivery)){repository.finish(delivery,"SKIPPED","READ_OR_DISABLED");continue;}gateway.deliver(delivery);repository.finish(delivery,"SENT",null);}
   catch(NotificationGateway.DeliveryFailure e){repository.finish(delivery,"RETRY",e.code());log.warn("Notification delivery deferred: {}",e.code());}
   catch(Exception e){log.warn("Notification processing interrupted; lease will recover");}
  }}catch(Exception e){log.warn("Notification queue temporarily unavailable");}
 }
}
