package pl.aniolstroz.audit;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.aniolstroz.contracts.api.AuditApi;
import pl.aniolstroz.contracts.api.CallsApi;

/**
 * /api/calls and /api/audit/summary, the interfaces are generated from contracts/openapi.yaml. Errors leave as
 * ProblemDetail through ApiExceptionHandler (API-06).
 */
@RestController
class AuditController implements CallsApi, AuditApi {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public ResponseEntity<List<CallSummary>> listCalls(Integer limit) {
        return ResponseEntity.ok(audit.calls(limit));
    }

    @Override
    public ResponseEntity<List<AuditRecord>> getCallAudit(String callId) {
        return ResponseEntity.ok(audit.callAudit(callId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Override
    public ResponseEntity<AuditSummary> getAuditSummary() {
        return ResponseEntity.ok(audit.summary());
    }
}
