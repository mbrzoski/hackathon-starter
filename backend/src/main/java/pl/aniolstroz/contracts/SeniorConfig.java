package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Configuration of the senior's account, set by the family. Never sent to Claude or STT (rule 5). */
public record SeniorConfig(
        @NotNull @Pattern(regexp = "^(\\+?[0-9 ]{9,15})?$") String familyPhone,
        @NotNull @Size(max = 30) List<@NotNull @Size(min = 2, max = 60) String> keywords) {
}
