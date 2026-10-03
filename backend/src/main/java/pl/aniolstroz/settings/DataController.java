package pl.aniolstroz.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.api.DataApi;

/** DELETE /api/data: the family erases everything stored (DAT-02). The interface is generated from openapi.yaml. */
@RestController
class DataController implements DataApi {

    private final RetentionService retention;

    DataController(RetentionService retention) {
        this.retention = retention;
    }

    @Override
    public ResponseEntity<Void> deleteAllData() {
        retention.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
