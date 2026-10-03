package pl.aniolstroz.stt;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aniolstroz.config.AppProperties;

/**
 * Chooses the speech recognizer: {@code app.stt.provider} is vosk (default, local and offline) or fake (tests). The
 * Vosk model is loaded lazily, so the application starts without it (SCRIPTED and MOCK need no model); only a LIVE
 * call without the model is refused, loudly (rule 7).
 */
@Configuration(proxyBeanMethods = false)
class SttConfig {

    @Bean
    SttProviderFactory sttProviderFactory(AppProperties properties) {
        if (properties.stt().provider() == AppProperties.Stt.Provider.FAKE) {
            return new FakeSttProviderFactory();
        }
        return new VoskSttProviderFactory(new VoskModelHolder(Path.of(properties.stt().vosk().modelPath())));
    }

    /** The recognition thread hands segments and statuses to virtual threads here (AUD-04). */
    @Bean(destroyMethod = "shutdown")
    ExecutorService sttExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService sttRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("stt-retry").daemon(true).factory());
    }

    @Bean
    RetryScheduler sttRetryScheduler(ScheduledExecutorService sttRetryExecutor) {
        return (Duration delay, Runnable task) -> sttRetryExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Creates {@link VoskSttProvider}s on the one shared model; closes the model when the application stops. */
    static final class VoskSttProviderFactory implements SttProviderFactory, AutoCloseable {

        private final VoskModelHolder models;

        VoskSttProviderFactory(VoskModelHolder models) {
            this.models = models;
        }

        @Override
        public void preflight() throws SttUnavailableException {
            models.model();
        }

        @Override
        public SttProvider create(SttStatusSink status) {
            return new VoskSttProvider(models, status);
        }

        @Override
        public void close() {
            models.close();
        }
    }

    /** No recognition at all: for tests and for trying the pipeline. Segments never appear on their own. */
    static final class FakeSttProviderFactory implements SttProviderFactory {

        @Override
        public void preflight() {
        }

        @Override
        public SttProvider create(SttStatusSink status) {
            return new FakeSttProvider(List.of());
        }
    }
}
