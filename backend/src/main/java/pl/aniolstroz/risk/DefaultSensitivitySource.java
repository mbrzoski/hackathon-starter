package pl.aniolstroz.risk;

import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.Sensitivity;

/** Always STANDARD until the settings task provides the household's choice. */
@Component
class DefaultSensitivitySource implements SensitivitySource {

    @Override
    public Sensitivity current() {
        return Sensitivity.STANDARD;
    }
}
