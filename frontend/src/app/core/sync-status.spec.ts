import { SyncStatus } from './api.models';
import { runDuration, syncSummary } from './sync-status';

const at = (hour: number, day = 6) => new Date(2026, 10, day, hour, 0).toISOString();
const status = (overrides: Partial<SyncStatus> = {}): SyncStatus => ({ lastSyncAt: at(14), nextSyncAt: at(15), lastRunFailed: false, collectionEnabled: true, ...overrides });
const now = new Date(2026, 10, 6, 14, 30);

describe('sync-status', () => {
    it('mostra a última sincronização e a hora da próxima no mesmo dia', () => {
        const summary = syncSummary(status(), now);
        expect(summary.last).toBe('Cotações de 06/11 14:00');
        expect(summary.next).toBe('próxima às 15:00');
        expect(summary.warning).toBeNull();
    });

    it('inclui a data quando a próxima sincronização é em outro dia', () => {
        expect(syncSummary(status({ nextSyncAt: at(0, 7) }), now).next).toBe('próxima em 07/11 00:00');
    });

    it('avisa quando ainda não houve sincronização', () => {
        expect(syncSummary(status({ lastSyncAt: null }), now).last).toBe('Aguardando a primeira sincronização');
    });

    it('avisa que a coleta está pausada', () => {
        expect(syncSummary(status({ collectionEnabled: false, nextSyncAt: null }), now).next).toBe('sincronização pausada');
    });

    it('não inventa horário sem agendamento', () => {
        expect(syncSummary(status({ nextSyncAt: null }), now).next).toBe('sem próxima sincronização agendada');
    });

    it('mantém o horário da última que atualizou preços e avisa sobre a falha', () => {
        const summary = syncSummary(status({ lastRunFailed: true }), now);
        expect(summary.last).toBe('Cotações de 06/11 14:00');
        expect(summary.warning).toContain('falhas');
    });

    it('calcula a duração em segundos', () => {
        expect(runDuration({ startedAt: '2026-11-06T14:00:00.000Z', finishedAt: '2026-11-06T14:00:01.250Z' })).toMatch(/^1,[23] s$/);
        expect(runDuration({ startedAt: '2026-11-06T14:00:00.000Z', finishedAt: null })).toBe('—');
    });
});
