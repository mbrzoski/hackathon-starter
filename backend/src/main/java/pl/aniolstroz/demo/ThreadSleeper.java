package pl.aniolstroz.demo;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Real pause; on a virtual thread this does not occupy a carrier thread. */
@Component
class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }
}
