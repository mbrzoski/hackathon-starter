package pl.aniolstroz.risk;

import pl.aniolstroz.contracts.Sensitivity;

/** Where the risk computation reads the household sensitivity from, at the moment it computes. */
@FunctionalInterface
public interface SensitivitySource {

    Sensitivity current();
}
