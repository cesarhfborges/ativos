import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MessageModule } from 'primeng/message';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { AdminService } from '@/app/core/admin.service';
import { AuditEntry } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';

const PAGE_SIZE = 20;

const ACTION_LABELS: Record<string, string> = {
    USER_CREATED: 'Conta criada',
    USER_UPDATED: 'Conta editada',
    USER_ACTIVATED: 'Conta ativada',
    USER_DEACTIVATED: 'Conta desativada',
    USER_PASSWORD_RESET: 'Senha redefinida',
    USER_SESSIONS_ENDED: 'Sessões encerradas',
    USER_PROMOTED: 'Conta promovida a admin',
    SETTING_CHANGED: 'Configuração alterada',
    SETTING_RESTORED: 'Configuração restaurada',
    QUOTES_COLLECTED: 'Cotações coletadas',
    POSITIONS_REBUILT: 'Posições recalculadas',
    ASSET_UPDATED: 'Ativo editado',
    ASSET_VERIFIED: 'Ativo verificado',
    ASSET_REMOVED: 'Ativo removido'
};

@Component({
    selector: 'app-admin-audit',
    imports: [DatePipe, MessageModule, TableModule],
    template: `
        <div class="mb-6">
            <h1 class="text-3xl! font-semibold m-0!">Auditoria</h1>
            <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Ações administrativas, da mais recente à mais antiga.</p>
        </div>

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
            }
            <div class="overflow-x-auto" tabindex="0" role="region" aria-label="Registro de auditoria">
                <p-table class="block min-w-[56rem]" dataKey="id" [value]="entries()" [lazy]="true" [paginator]="true" [rows]="pageSize" [totalRecords]="total()" [loading]="loading()" (onLazyLoad)="onPage($event)">
                    <ng-template #header>
                        <tr>
                            <th scope="col">Quando</th>
                            <th scope="col">Quem</th>
                            <th scope="col">Ação</th>
                            <th scope="col">Alvo</th>
                            <th scope="col">Detalhes</th>
                        </tr>
                    </ng-template>
                    <ng-template #body let-entry>
                        <tr>
                            <td class="whitespace-nowrap">{{ entry.createdAt | date: 'short' }}</td>
                            <td>{{ entry.actorEmail }}</td>
                            <td class="whitespace-nowrap">{{ actionLabel(entry.action) }}</td>
                            <td>{{ entry.target ?? '—' }}</td>
                            <td>{{ entry.details ?? '—' }}</td>
                        </tr>
                    </ng-template>
                    <ng-template #emptymessage>
                        <tr>
                            <td colspan="5" class="text-center text-muted-color">Nenhuma ação registrada.</td>
                        </tr>
                    </ng-template>
                </p-table>
            </div>
        </div>
    `
})
export class AdminAuditPage {
    private readonly admin = inject(AdminService);

    protected readonly pageSize = PAGE_SIZE;
    protected readonly entries = signal<AuditEntry[]>([]);
    protected readonly total = signal(0);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);

    protected actionLabel(action: string): string {
        return ACTION_LABELS[action] ?? action;
    }

    protected onPage(event: TableLazyLoadEvent): void {
        this.loading.set(true);
        this.admin.audit(Math.floor((event.first ?? 0) / PAGE_SIZE), PAGE_SIZE).subscribe({
            next: ({ items, total }) => {
                this.entries.set(items);
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
