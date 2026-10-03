package pl.aniolstroz.contracts;

/** Whether the device next to the phone listens. On unless the caretaker switched it off in the admin portal. */
public record Protection(boolean enabled) {
}
