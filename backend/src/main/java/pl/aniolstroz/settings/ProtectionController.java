package pl.aniolstroz.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.Protection;
import pl.aniolstroz.contracts.api.ProtectionApi;

/** GET and PUT /api/protection: the switch of the admin portal. The interface is generated from openapi.yaml. */
@RestController
class ProtectionController implements ProtectionApi {

    private final ProtectionService protection;

    ProtectionController(ProtectionService protection) {
        this.protection = protection;
    }

    @Override
    public ResponseEntity<Protection> getProtection() {
        return ResponseEntity.ok(new Protection(protection.enabled()));
    }

    @Override
    public ResponseEntity<Protection> setProtection(Protection body) {
        protection.set(body.enabled());
        return ResponseEntity.ok(body);
    }
}
