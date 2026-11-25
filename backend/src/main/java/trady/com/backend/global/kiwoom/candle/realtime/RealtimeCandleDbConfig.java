package trady.com.backend.global.kiwoom.candle.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
public class RealtimeCandleDbConfig {
    @Bean(destroyMethod = "dispose")
    public Scheduler realtimeCandleDbScheduler() {
        var sequence = new AtomicInteger();
        var executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(30), runnable -> {
                    var thread = new Thread(runnable, "realtime-candle-db-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return Schedulers.fromExecutorService(executor);
    }
}
