package tn.fastcoloc.api;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
/** Keep payload and JDBC bind logging disabled even under a developer's broad DEBUG setting. */
@Configuration
public class SafeLoggingConfig {
 @PostConstruct void restrictPayloadLogging(){
  if(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context){
   for(String name:new String[]{"org.springframework.web","org.springframework.jdbc","org.springframework.security","org.springframework.http"})context.getLogger(name).setLevel(ch.qos.logback.classic.Level.INFO);
   context.getLogger("org.hibernate.SQL").setLevel(ch.qos.logback.classic.Level.OFF);
   context.getLogger("org.hibernate.orm.jdbc.bind").setLevel(ch.qos.logback.classic.Level.OFF);
  }
 }
}
