package pl.aniolstroz.demo;

import java.time.Duration;

/** Injected pause between scripted segments, so tests do not really wait. */
@FunctionalInterface
interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
