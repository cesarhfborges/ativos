import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AdminService } from '@/app/core/admin.service';
import { AssetVerification, CatalogAsset } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { ConfirmService } from '@/app/core/confirm.service';
import { AssetDialog } from './components/asset-dialog';

const PAGE_SIZE = 20;

const VERIFICATION_RESULT: Record<AssetVerification['check'], { severity: 'success' | 'warn' | 'info'; summary: string }> = {
    FOUND: { severity: 'success', summary: 'Confirmado pelo provedor' },
    NOT_FOUND: { severity: 'warn', summary: 'O provedor não encontrou este símbolo' },
    UNAVAILABLE: { severity: 'info', summary: 'O provedor não respondeu; nada mudou' }
};

@Component({
    selector: 'app-admin-assets',
    imports: [AssetDialog, ButtonModule, DecimalPipe, InputTextModule, MessageModule, TableModule, TagModule],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Ativos</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">O catálogo é compartilhado: uma correção vale para todos os usuários.</p>
            </div>
            <input pInputText type="search" class="w-72 max-w-full" aria-label="Buscar por símbolo ou nome" placeholder="Buscar por símbolo ou nome" (input)="searches.next($any($event.target).value)" />
        </div>

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
            }
            <div class="stack-table stack-52">
                <p-table dataKey="id" [value]="assets()" [lazy]="true" [paginator]="true" [rows]="pageSize" [totalRecords]="total()" [loading]="loading()" (onLazyLoad)="onPage($event)">
                    <ng-template #header>
                        <tr>
                            <th scope="col">Ativo</th>
                            <th scope="col">Tipo</th>
                            <th scope="col">Verificação</th>
                            <th scope="col" class="text-right!">Posições abertas</th>
                            <th scope="col" class="text-right!">Operações</th>
                            <th scope="col" class="text-right!">Ações</th>
                        </tr>
                    </ng-template>
                    <ng-template #body let-asset>
                        <tr>
                            <td class="stack-full">
                                <div class="font-medium">{{ asset.symbol }}</div>
                                <div class="text-muted-color text-sm">{{ asset.name }}</div>
                            </td>
                            <td data-label="Tipo">{{ asset.assetType === 'CRYPTO' ? 'Cripto' : 'Ação' }}</td>
                            <td data-label="Verificação"><p-tag [value]="asset.verificationStatus === 'VERIFIED' ? 'Verificado' : 'Não verificado'" [severity]="asset.verificationStatus === 'VERIFIED' ? 'success' : 'warn'" /></td>
                            <td class="text-right tabular-nums" data-label="Posições abertas">{{ asset.openPositions | number }}</td>
                            <td class="text-right tabular-nums" data-label="Operações">{{ asset.transactions | number }}</td>
                            <td class="stack-actions text-right whitespace-nowrap">
                                <p-button label="Editar" size="small" severity="secondary" [text]="true" [ariaLabel]="'Editar ' + asset.symbol" (onClick)="edit(asset)" />
                                <p-button label="Verificar" size="small" severity="secondary" [text]="true" [ariaLabel]="'Verificar ' + asset.symbol + ' no provedor'" (onClick)="verify(asset)" />
                                @if (asset.transactions === 0) {
                                    <p-button label="Remover" size="small" severity="danger" [text]="true" [ariaLabel]="'Remover ' + asset.symbol" (onClick)="remove(asset)" />
                                }
                            </td>
                        </tr>
                    </ng-template>
                    <ng-template #emptymessage>
                        <tr>
                            <td colspan="6" class="text-center text-muted-color">Nenhum ativo encontrado.</td>
                        </tr>
                    </ng-template>
                </p-table>
            </div>
        </div>

        <app-asset-dialog [asset]="editing()" [(visible)]="dialogVisible" (saved)="onSaved($event)" />
    `
})
export class AdminAssetsPage {
    private readonly admin = inject(AdminService);
    private readonly confirm = inject(ConfirmService);
    private readonly messages = inject(MessageService);

    protected readonly pageSize = PAGE_SIZE;
    protected readonly searches = new Subject<string>();

    protected readonly assets = signal<CatalogAsset[]>([]);
    protected readonly total = signal(0);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);
    protected readonly editing = signal<CatalogAsset | null>(null);
    protected readonly dialogVisible = signal(false);

    private query = '';
    private page = 0;

    constructor() {
        this.searches.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((query) => {
            this.query = query;
            this.page = 0;
            this.load();
        });
    }

    protected onPage(event: TableLazyLoadEvent): void {
        this.page = Math.floor((event.first ?? 0) / PAGE_SIZE);
        this.load();
    }

    protected edit(asset: CatalogAsset): void {
        this.editing.set(asset);
        this.dialogVisible.set(true);
    }

    protected onSaved(asset: CatalogAsset): void {
        this.messages.add({ severity: 'success', closable: false, summary: 'Ativo atualizado', detail: asset.symbol });
        this.load();
    }

    protected verify(asset: CatalogAsset): void {
        this.admin.verifyAsset(asset.id).subscribe({
            next: ({ check }) => {
                this.messages.add({ severity: VERIFICATION_RESULT[check].severity, closable: false, summary: VERIFICATION_RESULT[check].summary, detail: asset.symbol });
                this.load();
            },
            error: (error: unknown) => this.notifyError(error)
        });
    }

    protected remove(asset: CatalogAsset): void {
        this.confirm.ask({ header: 'Remover ativo', message: `Remover ${asset.symbol} do catálogo? Ele não tem nenhuma operação registrada.`, acceptLabel: 'Remover', danger: true }, () =>
            this.admin.removeAsset(asset.id).subscribe({
                next: () => {
                    this.messages.add({ severity: 'success', closable: false, summary: 'Ativo removido', detail: asset.symbol });
                    this.load();
                },
                error: (error: unknown) => this.notifyError(error)
            })
        );
    }

    private notifyError(error: unknown): void {
        this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível concluir', detail: errorMessage(error) });
    }

    private load(): void {
        this.loading.set(true);
        this.admin.assets(this.query, this.page, PAGE_SIZE).subscribe({
            next: ({ items, total }) => {
                this.assets.set(items);
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
