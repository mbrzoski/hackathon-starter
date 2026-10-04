package pl.aniolstroz.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.api.DataApi;

/**
 * DELETE /api/data: the family erases everything stored; DELETE /api/calls/{callId}: everything about one call (DAT-02).
 * The interface is generated from openapi.yaml.
 */
@RestController
class DataController implements DataApi {

    private final RetentionService retention;
    private final pl.aniolstroz.alerts.LiveCallAccess liveCall;

    DataController(RetentionService retention, pl.aniolstroz.alerts.LiveCallAccess liveCall) {
        this.retention = retention;
        this.liveCall = liveCall;
    }

    @Override
    public ResponseEntity<Void> deleteCallData(String callId) {
        if (liveCall.isActive(callId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Rozmowa jeszcze trwa. Dane można usunąć po jej zakończeniu.");
        }
        if (!retention.deleteCall(callId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Nie ma zapisanych danych tej rozmowy.");
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteAllData() {
        retention.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
