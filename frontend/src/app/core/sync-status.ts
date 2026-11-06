import { differenceInMilliseconds, format, isSameDay, parseISO } from 'date-fns';
import { SyncRun, SyncStatus } from './api.models';

export interface SyncSummary {
    last: string;
    next: string;
    warning: string | null;
}

/** Texto do indicador de sincronização. Nas telas, tudo fica no fuso de quem está vendo. */
export function syncSummary(status: SyncStatus, now: Date): SyncSummary {
    return {
        last: status.lastSyncAt ? `Cotações de ${format(parseISO(status.lastSyncAt), 'dd/MM HH:mm')}` : 'Aguardando a primeira sincronização',
        next: nextSyncLabel(status, now),
        warning: status.lastRunFailed ? 'A última sincronização teve falhas. Alguns preços podem estar desatualizados.' : null
    };
}

function nextSyncLabel(status: SyncStatus, now: Date): string {
    if (!status.collectionEnabled) {
        return 'sincronização pausada';
    }
    if (!status.nextSyncAt) {
        return 'sem próxima sincronização agendada';
    }
    const next = parseISO(status.nextSyncAt);
    return isSameDay(next, now) ? `próxima às ${format(next, 'HH:mm')}` : `próxima em ${format(next, 'dd/MM HH:mm')}`;
}

/** Ainda sem término, a execução não tem duração para mostrar. */
export function runDuration(run: Pick<SyncRun, 'startedAt' | 'finishedAt'>): string {
    if (!run.finishedAt) {
        return '—';
    }
    const seconds = differenceInMilliseconds(parseISO(run.finishedAt), parseISO(run.startedAt)) / 1000;
    return `${seconds.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} s`;
}
