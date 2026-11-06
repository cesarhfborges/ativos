import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { SyncStatus } from './api.models';

/** Guarda a situação da sincronização, lida do banco: consultar aqui nunca chama os provedores. */
@Injectable({ providedIn: 'root' })
export class MarketSyncService {
    private readonly http = inject(HttpClient);

    readonly status = signal<SyncStatus | null>(null);

    /** Uma falha mantém o último valor: o indicador é informativo e não deve incomodar. */
    refresh(): void {
        this.http.get<SyncStatus>('/api/market/sync-status').subscribe({
            next: (status) => this.status.set(status),
            error: () => undefined
        });
    }
}
