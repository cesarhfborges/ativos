package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.sharedkernel.market.QuotesCollectedEvent;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuoteCollectionJob {

    private final QuoteCollector collector;
    private final ApplicationEventPublisher events;
    private final TaskScheduler scheduler;
    private final boolean collectOnStartup;
    private final AppSettings appSettings;

    public QuoteCollectionJob(
            QuoteCollector collector,
            ApplicationEventPublisher events,
            TaskScheduler scheduler,
            @Value("${market.collect-on-startup}") boolean collectOnStartup,
            AppSettings appSettings) {
        this.appSettings = appSettings;
        this.collector = collector;
        this.events = events;
        this.scheduler = scheduler;
        this.collectOnStartup = collectOnStartup;
    }

    /** Só a coleta agendada gera snapshots, para que o histórico mantenha um ponto por hora. */
    @Scheduled(cron = "${market.collection-cron}")
    public void collectHourly() {
        if (collectionEnabled()) {
            events.publishEvent(new QuotesCollectedEvent(collector.collect()));
        }
    }

    /** Em outra thread: as chamadas externas não podem atrasar o início da aplicação. */
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        if (collectOnStartup && collectionEnabled()) {
            scheduler.schedule(collector::collect, Instant.now());
        }
    }

    private boolean collectionEnabled() {
        return appSettings.current().getBoolean(SettingKeys.COLLECTION_ENABLED);
    }
}
