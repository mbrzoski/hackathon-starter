package pl.aniolstroz.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Household settings; never sent to Claude or STT. */
public record Settings(
        boolean seniorConsent,
        boolean familyConsent,
        @NotNull @Size(max = 5) List<@Valid Contact> contacts,
        @NotNull Sensitivity sensitivity,
        @Min(1) @Max(90) int retentionDays) {

    public record Contact(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ]{9,15}$") String phone) {
    }
}
