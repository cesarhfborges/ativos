import { Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { differenceInMilliseconds, parseISO } from 'date-fns';
import { filter } from 'rxjs';
import { MarketSyncService } from '@/app/core/market-sync.service';
import { syncSummary } from '@/app/core/sync-status';

/** Tempo para a coleta terminar antes de ler o novo horário. */
const AFTER_SYNC_MS = 60_000;

/**
 * Atualiza ao navegar e logo depois de cada sincronização, sem consulta periódica:
 * requisições frequentes impediriam a sessão de expirar com a aba aberta e parada.
 */
@Component({
    selector: 'app-sync-indicator',
    template: `
        @if (summary(); as data) {
            <p class="flex items-center gap-2 m-0 text-xs sm:text-sm leading-tight sm:whitespace-nowrap text-surface-600 dark:text-surface-300" [attr.title]="data.warning">
                <i class="pi pi-sync hidden! sm:inline-block!" aria-hidden="true"></i>
                <span class="flex flex-col md:flex-row md:gap-1">
                    <span>{{ data.last }}</span>
                    <span><span class="hidden md:inline" aria-hidden="true">· </span>{{ data.next }}</span>
                </span>
                @if (data.warning; as warning) {
                    <span class="flex items-center gap-1 font-medium text-orange-700 dark:text-orange-300">
                        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
                        <span class="hidden xl:inline">Com falhas</span>
                        <span class="sr-only xl:hidden">{{ warning }}</span>
                    </span>
                }
            </p>
        }
    `
})
export class SyncIndicator {
    private readonly sync = inject(MarketSyncService);

    protected readonly summary = computed(() => {
        const status = this.sync.status();
        return status ? syncSummary(status, new Date()) : null;
    });

    constructor() {
        this.sync.refresh();
        inject(Router)
            .events.pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed()
            )
            .subscribe(() => this.sync.refresh());

        effect((onCleanup) => {
            const next = this.sync.status()?.nextSyncAt;
            if (!next) {
                return;
            }
            const wait = Math.max(differenceInMilliseconds(parseISO(next), new Date()), 0) + AFTER_SYNC_MS;
            const timer = setTimeout(() => this.sync.refresh(), wait);
            onCleanup(() => clearTimeout(timer));
        });
    }
}
