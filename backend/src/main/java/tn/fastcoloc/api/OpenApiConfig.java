package tn.fastcoloc.api;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
 @Bean OpenAPI fastcolocApi(){return new OpenAPI().info(new Info().title("FastColoc Sprints 1 et 2 API").version("v1").description("Auth Supabase, inscription en cinq étapes, profils privés et administration Web. Sprint 2 : offres et demandes, brouillons, publication et renouvellement, recherche paginée, filtres géographiques, photos privées et favoris. Le catalogue et ses localités sont publics ; les autres opérations d'annonces requièrent un compte ACTIVE confirmé. Publication personnelle uniquement, sans modération Sprint 3."))
  .components(new Components().addSecuritySchemes("supabaseBearer",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
 }
}
