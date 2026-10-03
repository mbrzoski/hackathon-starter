package pl.aniolstroz.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.SeniorConfig;
import pl.aniolstroz.contracts.api.SeniorConfigApi;

/** GET and PUT /api/senior-config: the family sets the number the senior may call. The interface is generated. */
@RestController
class SeniorConfigController implements SeniorConfigApi {

    private final SeniorConfigService config;

    SeniorConfigController(SeniorConfigService config) {
        this.config = config;
    }

    @Override
    public ResponseEntity<SeniorConfig> getSeniorConfig() {
        return ResponseEntity.ok(config.current());
    }

    @Override
    public ResponseEntity<SeniorConfig> setSeniorConfig(SeniorConfig body) {
        return ResponseEntity.ok(config.save(body));
    }
}
