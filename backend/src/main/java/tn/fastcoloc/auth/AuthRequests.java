package tn.fastcoloc.auth;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
public final class AuthRequests {
 private AuthRequests(){}
 public record Signup(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,
    @NotBlank @Size(min=12,max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password,
    @Pattern(regexp="[A-Za-z0-9_-]{43}") String codeChallenge){}
 public record Login(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,
    @NotBlank @Size(max=256) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password){}
 public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,
    @Pattern(regexp="[A-Za-z0-9_-]{43}") String codeChallenge){}
 public record Refresh(@NotBlank @Size(max=4096) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String refreshToken){}
 public record Password(@NotBlank @Size(min=12,max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password){}
 public record Exchange(@NotBlank @Size(max=4096) String code,
    @NotNull @Pattern(regexp="[A-Za-z0-9._~-]{43,128}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String codeVerifier){}
 public record Verify(@NotBlank @Size(max=256) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String tokenHash,
    @NotNull @Pattern(regexp="signup|recovery|email") String type){}
}
