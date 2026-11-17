package tn.fastcoloc.profiles;

import jakarta.validation.constraints.*;

/** Only descriptive fields: no id, role, status or completion field accepted. */
public record SyncRequest(@Size(max=100) String firstName, @Size(max=100) String lastName,
                          @Size(max=80) String gender, @Min(0) @Max(130) Short age) {}
