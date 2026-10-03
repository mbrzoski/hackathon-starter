package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Configuration of the senior's account, set by the family. Never sent to Claude or STT (rule 5). */
public record SeniorConfig(@NotNull @Pattern(regexp = "^(\\+?[0-9 ]{9,15})?$") String familyPhone) {
}
