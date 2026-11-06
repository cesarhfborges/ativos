import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { AdminService } from '@/app/core/admin.service';
import { AdminOverview } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { ConfirmService } from '@/app/core/confirm.service';

@Component({
    selector: 'app-admin-overview',
    imports: [ButtonModule, DatePipe, DecimalPipe, MessageModule, RouterLink, SkeletonModule],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Visão geral</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Totais do sistema e saúde das cotações. As carteiras de cada usuário continuam privadas.</p>
            </div>
            <div class="flex flex-wrap gap-2">
                <p-button label="Recalcular posições" icon="pi pi-calculator" severity="secondary" [outlined]="true" [loading]="rebuilding()" (onClick)="rebuild()" />
            </div>
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" (onClick)="load()" />
            </div>
        } @else if (overview(); as data) {
            <div class="flex flex-col gap-8">
                <section aria-label="Totais" class="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    @for (card of cards(); track card.label) {
                        <div class="card mb-0!">
                            <div class="text-muted-color font-medium mb-2">{{ card.label }}</div>
                            <div class="text-surface-900 dark:text-surface-0 font-medium text-2xl tabular-nums">{{ card.value | number }}</div>
                            <div class="text-muted-color text-sm mt-1">{{ card.hint }}</div>
                        </div>
                    }
                </section>

                <section class="card mb-0!" aria-labelledby="admin-quotes-title">
                    <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
                        <h2 id="admin-quotes-title" class="text-xl! font-semibold m-0!">Cotações</h2>
                        <a routerLink="/admin/sincronizacao" class="underline font-medium">Ver sincronização</a>
                    </div>
                    @if (!data.collectionEnabled) {
                        <p-message severity="warn" styleClass="mb-4">
                            A coleta automática está pausada. Para retomar, acesse&nbsp;<a routerLink="/admin/configuracoes" class="underline font-medium">Configurações</a>.
                        </p-message>
                    }
                    <dl class="grid grid-cols-1 sm:grid-cols-2 gap-4 m-0">
                        <div>
                            <dt class="text-muted-color text-sm">Última coleta</dt>
                            <dd class="m-0 font-medium">{{ data.lastCollectedAt ? (data.lastCollectedAt | date: 'short') : 'Nenhuma ainda' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Símbolos coletados</dt>
                            <dd class="m-0 font-medium">{{ data.activeSymbols | number }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Sem nenhuma cotação</dt>
                            <dd class="m-0 font-medium">{{ data.symbolsWithoutQuote.join(', ') || 'Nenhum' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Sem atualização há mais de 2 horas</dt>
                            <dd class="m-0 font-medium">{{ data.symbolsWithStaleQuote.join(', ') || 'Nenhum' }}</dd>
                        </div>
                    </dl>
                </section>
            </div>
        } @else {
            <p-skeleton height="20rem" />
        }
    `
})
export class AdminOverviewPage {
    private readonly admin = inject(AdminService);
    private readonly confirm = inject(ConfirmService);
    private readonly messages = inject(MessageService);

    protected readonly overview = signal<AdminOverview | null>(null);
    protected readonly failure = signal<string | null>(null);
    protected readonly rebuilding = signal(false);

    protected readonly cards = computed(() => {
        const data = this.overview();
        return data
            ? [
                  { label: 'Usuários', value: data.totalUsers, hint: `${data.activeUsers} ativos` },
                  { label: 'Administradores', value: data.admins, hint: 'Ativos' },
                  { label: 'Carteiras', value: data.wallets, hint: 'Com alguma operação' },
                  { label: 'Operações', value: data.transactions, hint: 'Sem contar estornos' }
              ]
            : [];
    });

    constructor() {
        this.load();
    }

    protected load(): void {
        this.admin.overview().subscribe({
            next: (overview) => {
                this.overview.set(overview);
                this.failure.set(null);
            },
            error: (error: unknown) => this.failure.set(errorMessage(error))
        });
    }

    protected rebuild(): void {
        this.confirm.ask(
            {
                header: 'Recalcular posições',
                message: 'Refazer as posições de todos os usuários a partir das operações? O preço médio e o lucro das vendas serão recalculados com as regras atuais. O histórico patrimonial não muda.',
                acceptLabel: 'Recalcular'
            },
            () => {
                this.rebuilding.set(true);
                this.admin.rebuildPositions().subscribe({
                    next: ({ wallets, positions }) => {
                        this.rebuilding.set(false);
                        this.messages.add({ severity: 'success', closable: false, summary: 'Posições recalculadas', detail: `${positions} posições em ${wallets} carteiras.` });
                    },
                    error: (error: unknown) => {
                        this.rebuilding.set(false);
                        this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível recalcular', detail: errorMessage(error) });
                    }
                });
            }
        );
    }
}
