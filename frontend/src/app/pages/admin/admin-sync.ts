import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule, TableRowExpandEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AdminService } from '@/app/core/admin.service';
import { AdminOverview, SyncItem, SyncItemStatus, SyncOrigin, SyncRun, SyncRunStatus } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { MarketSyncService } from '@/app/core/market-sync.service';
import { runDuration, syncSummary } from '@/app/core/sync-status';

const PAGE_SIZE = 20;
const SEARCH_DELAY_MS = 400;

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary';

const ORIGIN_LABELS: Record<SyncOrigin, string> = {
    SCHEDULED: 'Agendada',
    STARTUP: 'Início da aplicação',
    ADMIN: 'Admin',
    NEW_SYMBOL: 'Símbolo novo'
};

const RUN_STATUS: Record<SyncRunStatus, { label: string; severity: Severity }> = {
    RUNNING: { label: 'Em andamento', severity: 'info' },
    SUCCESS: { label: 'Sucesso', severity: 'success' },
    PARTIAL: { label: 'Parcial', severity: 'warn' },
    FAILED: { label: 'Falha', severity: 'danger' },
    INTERRUPTED: { label: 'Interrompida', severity: 'secondary' }
};

const ITEM_STATUS: Record<SyncItemStatus, { label: string; severity: Severity }> = {
    UPDATED: { label: 'Atualizado', severity: 'success' },
    NO_DATA: { label: 'Sem dados', severity: 'secondary' },
    FAILED: { label: 'Falhou', severity: 'danger' }
};

const options = <T extends string>(all: string, labels: Record<T, string>) => [{ label: all, value: null as T | null }, ...(Object.keys(labels) as T[]).map((value) => ({ label: labels[value], value: value as T | null }))];

type LoadedItems = SyncItem[] | 'loading' | 'failed';

@Component({
    selector: 'app-admin-sync',
    imports: [ButtonModule, DatePipe, DecimalPipe, FormsModule, InputTextModule, MessageModule, RouterLink, SelectModule, TableModule, TagModule],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Sincronização</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Execuções da coleta de cotações, da mais recente à mais antiga.</p>
            </div>
            <p-button label="Coletar agora" icon="pi pi-refresh" severity="secondary" [outlined]="true" [loading]="collecting()" (onClick)="collect()" />
        </div>

        <div class="flex flex-col gap-8">
            <section class="card mb-0!" aria-labelledby="sync-summary-title">
                <h2 id="sync-summary-title" class="text-xl! font-semibold mt-0! mb-4!">Situação</h2>
                @if (sync.status()?.collectionEnabled === false) {
                    <p-message severity="warn" styleClass="mb-4">
                        A coleta automática está pausada. Para retomar, acesse&nbsp;<a routerLink="/admin/configuracoes" class="underline font-medium">Configurações</a>.
                    </p-message>
                }
                <dl class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 m-0">
                    <div>
                        <dt class="text-surface-600 dark:text-surface-300 text-sm">Última sincronização</dt>
                        <dd class="m-0 font-medium">{{ sync.status()?.lastSyncAt ? (sync.status()?.lastSyncAt | date: 'short') : 'Nenhuma ainda' }}</dd>
                    </div>
                    <div>
                        <dt class="text-surface-600 dark:text-surface-300 text-sm">Próxima sincronização</dt>
                        <dd class="m-0 font-medium">{{ sync.status()?.nextSyncAt ? (sync.status()?.nextSyncAt | date: 'short') : nextFallback() }}</dd>
                    </div>
                    <div>
                        <dt class="text-surface-600 dark:text-surface-300 text-sm">Símbolos coletados</dt>
                        <dd class="m-0 font-medium">{{ overview()?.activeSymbols ?? 0 | number }}</dd>
                    </div>
                    <div>
                        <dt class="text-surface-600 dark:text-surface-300 text-sm">Sem nenhuma cotação</dt>
                        <dd class="m-0 font-medium">{{ overview()?.symbolsWithoutQuote?.join(', ') || 'Nenhum' }}</dd>
                    </div>
                    <div>
                        <dt class="text-surface-600 dark:text-surface-300 text-sm">Sem atualização há mais de 2 horas</dt>
                        <dd class="m-0 font-medium">{{ overview()?.symbolsWithStaleQuote?.join(', ') || 'Nenhum' }}</dd>
                    </div>
                </dl>
            </section>

            <section class="card mb-0!" aria-labelledby="sync-runs-title">
                <h2 id="sync-runs-title" class="text-xl! font-semibold mt-0! mb-4!">Registros</h2>
                <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
                    <div class="flex flex-col gap-2">
                        <label id="sync-status-label" class="font-medium">Situação</label>
                        <p-select ariaLabelledBy="sync-status-label" optionLabel="label" optionValue="value" [fluid]="true" [options]="statusOptions" [ngModel]="status()" (ngModelChange)="setStatus($event)" />
                    </div>
                    <div class="flex flex-col gap-2">
                        <label id="sync-origin-label" class="font-medium">Origem</label>
                        <p-select ariaLabelledBy="sync-origin-label" optionLabel="label" optionValue="value" [fluid]="true" [options]="originOptions" [ngModel]="origin()" (ngModelChange)="setOrigin($event)" />
                    </div>
                    <div class="flex flex-col gap-2">
                        <label for="sync-symbol" class="font-medium">Símbolo</label>
                        <input id="sync-symbol" pInputText class="w-full" autocomplete="off" [ngModel]="symbol()" (ngModelChange)="symbolTyped.next($event)" />
                    </div>
                </div>

                @if (failure(); as message) {
                    <p-message severity="error" [text]="message" styleClass="mb-4" />
                }
                <div class="stack-table stack-56">
                    <p-table dataKey="id" [value]="runs()" [lazy]="true" [paginator]="true" [rows]="pageSize" [first]="first()" [totalRecords]="total()" [loading]="loading()" (onLazyLoad)="onPage($event)" (onRowExpand)="onExpand($event)">
                        <ng-template #header>
                            <tr>
                                <th scope="col" class="w-12"><span class="sr-only">Detalhes</span></th>
                                <th scope="col">Quando</th>
                                <th scope="col">Origem</th>
                                <th scope="col">Situação</th>
                                <th scope="col">Atualizados</th>
                                <th scope="col">Duração</th>
                                <th scope="col">Mensagem</th>
                            </tr>
                        </ng-template>
                        <ng-template #body let-run let-expanded="expanded">
                            <tr>
                                <td class="stack-toggle">
                                    <button type="button" pButton [pRowToggler]="run" [text]="true" [rounded]="true" severity="secondary" [attr.aria-label]="(expanded ? 'Ocultar' : 'Mostrar') + ' símbolos da execução de ' + (run.startedAt | date: 'short')" [attr.aria-expanded]="expanded">
                                        <i class="pi" [class.pi-chevron-down]="expanded" [class.pi-chevron-right]="!expanded" pButtonIcon aria-hidden="true"></i>
                                    </button>
                                </td>
                                <td class="stack-full whitespace-nowrap" data-label="Quando">{{ run.startedAt | date: 'short' }}</td>
                                <td class="whitespace-nowrap" data-label="Origem">{{ originLabel(run.origin) }}</td>
                                <td data-label="Situação"><p-tag [value]="runStatus(run.status).label" [severity]="runStatus(run.status).severity" /></td>
                                <td class="whitespace-nowrap tabular-nums" data-label="Atualizados">{{ run.updatedSymbols }} de {{ run.requestedSymbols }}</td>
                                <td class="whitespace-nowrap tabular-nums" data-label="Duração">{{ duration(run) }}</td>
                                <td class="stack-full wrap-anywhere" data-label="Mensagem">{{ run.message ?? '—' }}</td>
                            </tr>
                        </ng-template>
                        <ng-template #expandedrow let-run>
                            <tr>
                                <td colspan="7">
                                    @let loaded = items()[run.id];
                                    @if (loaded === 'failed') {
                                        <p-message severity="error" text="Não foi possível carregar os símbolos desta execução." />
                                    } @else if (loaded === 'loading' || !loaded) {
                                        <span class="text-surface-600 dark:text-surface-300">Carregando…</span>
                                    } @else if (loaded.length === 0) {
                                        <span class="text-surface-600 dark:text-surface-300">Nenhum símbolo nesta execução.</span>
                                    } @else {
                                        <table class="w-full text-left">
                                            <caption class="sr-only">Resultado de cada símbolo</caption>
                                            <thead>
                                                <tr>
                                                    <th scope="col" class="p-2">Símbolo</th>
                                                    <th scope="col" class="p-2">Busca</th>
                                                    <th scope="col" class="p-2">Resultado</th>
                                                    <th scope="col" class="p-2">Detalhes</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                @for (item of loaded; track item.symbol + item.kind) {
                                                    <tr>
                                                        <td class="p-2 font-medium" data-label="Símbolo">{{ item.symbol }}</td>
                                                        <td class="p-2" data-label="Busca">{{ item.kind === 'QUOTE' ? 'Cotação' : 'Histórico' }}</td>
                                                        <td class="p-2" data-label="Resultado"><p-tag [value]="itemStatus(item.status).label" [severity]="itemStatus(item.status).severity" /></td>
                                                        <td class="stack-full p-2 wrap-anywhere" data-label="Detalhes">{{ item.message ?? (item.points !== null ? item.points + ' fechamentos diários' : '—') }}</td>
                                                    </tr>
                                                }
                                            </tbody>
                                        </table>
                                    }
                                </td>
                            </tr>
                        </ng-template>
                        <ng-template #emptymessage>
                            <tr>
                                <td colspan="7" class="text-center text-surface-600 dark:text-surface-300">Nenhuma execução encontrada.</td>
                            </tr>
                        </ng-template>
                    </p-table>
                </div>
            </section>
        </div>
    `
})
export class AdminSyncPage {
    private readonly admin = inject(AdminService);
    private readonly messages = inject(MessageService);
    protected readonly sync = inject(MarketSyncService);

    protected readonly pageSize = PAGE_SIZE;
    protected readonly statusOptions = options<SyncRunStatus>('Todas', {
        RUNNING: RUN_STATUS.RUNNING.label,
        SUCCESS: RUN_STATUS.SUCCESS.label,
        PARTIAL: RUN_STATUS.PARTIAL.label,
        FAILED: RUN_STATUS.FAILED.label,
        INTERRUPTED: RUN_STATUS.INTERRUPTED.label
    });
    protected readonly originOptions = options<SyncOrigin>('Todas', ORIGIN_LABELS);

    protected readonly overview = signal<AdminOverview | null>(null);
    protected readonly runs = signal<SyncRun[]>([]);
    protected readonly total = signal(0);
    protected readonly first = signal(0);
    protected readonly loading = signal(true);
    protected readonly collecting = signal(false);
    protected readonly failure = signal<string | null>(null);
    protected readonly status = signal<SyncRunStatus | null>(null);
    protected readonly origin = signal<SyncOrigin | null>(null);
    protected readonly symbol = signal('');
    protected readonly items = signal<Record<string, LoadedItems>>({});
    protected readonly symbolTyped = new Subject<string>();

    protected readonly nextFallback = computed(() => {
        const status = this.sync.status();
        return status ? syncSummary(status, new Date()).next.replace(/^./, (letter) => letter.toUpperCase()) : '—';
    });

    constructor() {
        this.loadOverview();
        this.sync.refresh();
        this.symbolTyped.pipe(debounceTime(SEARCH_DELAY_MS), distinctUntilChanged(), takeUntilDestroyed()).subscribe((symbol) => {
            this.symbol.set(symbol);
            this.restart();
        });
    }

    protected originLabel(origin: SyncOrigin): string {
        return ORIGIN_LABELS[origin];
    }

    protected runStatus(status: SyncRunStatus) {
        return RUN_STATUS[status];
    }

    protected itemStatus(status: SyncItemStatus) {
        return ITEM_STATUS[status];
    }

    protected duration(run: SyncRun): string {
        return runDuration(run);
    }

    protected setStatus(status: SyncRunStatus | null): void {
        this.status.set(status);
        this.restart();
    }

    protected setOrigin(origin: SyncOrigin | null): void {
        this.origin.set(origin);
        this.restart();
    }

    protected onPage(event: TableLazyLoadEvent): void {
        this.first.set(event.first ?? 0);
        this.loadRuns();
    }

    protected onExpand(event: TableRowExpandEvent): void {
        const id = (event.data as SyncRun).id;
        this.items.update((items) => ({ ...items, [id]: 'loading' }));
        this.admin.syncRunItems(id).subscribe({
            next: (loaded) => this.items.update((items) => ({ ...items, [id]: loaded })),
            error: () => this.items.update((items) => ({ ...items, [id]: 'failed' }))
        });
    }

    protected collect(): void {
        this.collecting.set(true);
        this.admin.collectQuotes().subscribe({
            next: ({ refreshed }) => {
                this.collecting.set(false);
                this.messages.add(
                    refreshed
                        ? { severity: 'success', closable: false, summary: 'Coleta concluída', detail: 'Confira o resultado nos registros.' }
                        : { severity: 'info', closable: false, summary: 'Coleta recente', detail: 'Aguarde o intervalo mínimo entre coletas.' }
                );
                this.sync.refresh();
                this.loadOverview();
                this.restart();
            },
            error: (error: unknown) => {
                this.collecting.set(false);
                this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível coletar', detail: errorMessage(error) });
            }
        });
    }

    private restart(): void {
        this.first.set(0);
        this.loadRuns();
    }

    private loadOverview(): void {
        // Secundário: sem estes números, os registros continuam úteis.
        this.admin.overview().subscribe({ next: (overview) => this.overview.set(overview), error: () => undefined });
    }

    private loadRuns(): void {
        this.loading.set(true);
        const page = Math.floor(this.first() / PAGE_SIZE);
        this.admin.syncRuns({ status: this.status(), origin: this.origin(), symbol: this.symbol() }, page, PAGE_SIZE).subscribe({
            next: ({ items, total }) => {
                this.runs.set(items);
                this.total.set(total);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }
}
