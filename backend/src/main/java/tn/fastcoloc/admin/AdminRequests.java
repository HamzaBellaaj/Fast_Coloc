package tn.fastcoloc.admin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tn.fastcoloc.onboarding.StepRequests;
public final class AdminRequests {
 private AdminRequests(){}
 public record Status(@NotNull @Pattern(regexp="ACTIVE|BLOCKED")String status,@NotBlank @Size(max=1000)String reason){}
 public record Contacts(@NotNull @Valid StepRequests.Contacts contacts,@NotBlank @Size(max=1000)String reason){}
 public record Deletion(@NotBlank @Size(max=1000)String reason){}
}
