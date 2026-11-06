package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.dto.SyncItemResponse;
import br.com.chfb.ativo.markettracker.dto.SyncRunPage;
import br.com.chfb.ativo.markettracker.dto.SyncStatusResponse;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import br.com.chfb.ativo.markettracker.repository.SyncLogRepository;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Registro das coletas. Gravar é secundário: uma falha aqui vai para o log e nunca interrompe a coleta. */
@Service
public class SyncLog {

    private static final Logger log = LoggerFactory.getLogger(SyncLog.class);

    private final SyncLogRepository repository;
    private final AppSettings appSettings;
    private final String collectionCron;

    public SyncLog(
            SyncLogRepository repository,
            AppSettings appSettings,
            @Value("${market.collection-cron}") String collectionCron) {
        this.repository = repository;
        this.appSettings = appSettings;
        this.collectionCron = collectionCron;
    }

    SyncRunRecord start(SyncOrigin origin) {
        SyncRunRecord run = new SyncRunRecord(origin);
        safely("abrir", () -> repository.insertRunning(run.id(), origin, run.startedAt()));
        return run;
    }

    void finish(SyncRunRecord run) {
        close(run, run.status(), null);
    }

    void fail(SyncRunRecord run, RuntimeException cause) {
        close(run, SyncRunStatus.FAILED, cause.getMessage());
    }

    void markInterrupted() {
        safely("encerrar", () -> {
            int interrupted = repository.markInterrupted(Instant.now());
            if (interrupted > 0) {
                log.warn("{} coletas foram interrompidas pela parada da aplicação", interrupted);
            }
        });
    }

    /** Apaga o que passou do prazo configurado. */
    void purge() {
        safely("limpar", () -> {
            int days = appSettings.current().getInt(SettingKeys.SYNC_LOG_RETENTION_DAYS);
            repository.deleteStartedBefore(Instant.now().minus(Duration.ofDays(days)));
        });
    }

    public SyncStatusResponse status() {
        boolean enabled = appSettings.current().getBoolean(SettingKeys.COLLECTION_ENABLED);
        return new SyncStatusResponse(
                repository.findLastSyncAt(), enabled ? nextRun() : null, repository.lastGeneralRunFailed(), enabled);
    }

    public Instant lastSyncAt() {
        return repository.findLastSyncAt();
    }

    Instant lastGeneralRunStartedAt() {
        return repository.findLastGeneralRunStartedAt();
    }

    public SyncRunPage runs(SyncOrigin origin, SyncRunStatus status, String symbol, int page, int size) {
        return new SyncRunPage(
                repository.findRuns(origin, status, symbol, page, size), repository.countRuns(origin, status, symbol));
    }

    public List<SyncItemResponse> items(UUID runId) {
        return repository.findItems(runId);
    }

    private void close(SyncRunRecord run, SyncRunStatus status, String message) {
        safely("gravar", () -> repository.finish(
                run.id(), status, Instant.now(), run.requestedSymbols(), run.updatedSymbols(), message, run.items()));
    }

    /** O Spring aceita "-" para desligar o agendamento: nesse caso, não há próxima execução. */
    private Instant nextRun() {
        if (!CronExpression.isValidExpression(collectionCron)) {
            return null;
        }
        ZonedDateTime next = CronExpression.parse(collectionCron).next(ZonedDateTime.now());
        return next == null ? null : next.toInstant();
    }

    private static void safely(String action, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.warn("Não foi possível {} o registro da coleta: {}", action, e.getMessage());
        }
    }
}
