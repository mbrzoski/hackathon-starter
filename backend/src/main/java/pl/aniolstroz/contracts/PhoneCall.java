package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;

/** The simulated incoming phone call of the demo. Nothing is dialled; the number is made up. */
public record PhoneCall(boolean active, @NotBlank String number) {
}
