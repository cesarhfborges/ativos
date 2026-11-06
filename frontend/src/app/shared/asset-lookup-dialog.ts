import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, debounceTime, map, of, switchMap, tap } from 'rxjs';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AssetCandidate, AssetLookup, AssetType } from '@/app/core/api.models';
import { WalletService } from '@/app/core/wallet.service';

const MIN_QUERY_LENGTH = 2;
const SEARCH_DELAY_MS = 300;

/** Procura o ativo no provedor de cotações, inclusive os que ainda não existem no catálogo. */
@Component({
    selector: 'app-asset-lookup-dialog',
    imports: [CurrencyPipe, DialogModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectButtonModule],
    template: `<p-dialog header="Buscar ativo" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '30rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible" (onHide)="closed.emit()">
        <form class="flex flex-col gap-4" novalidate [formGroup]="form">
            <!-- Primeiro campo de propósito: é nele que o p-dialog põe o foco ao abrir. -->
            <div class="flex flex-col gap-2">
                <label for="lookup-query" class="font-medium">Nome ou símbolo</label>
                <input pInputText id="lookup-query" class="w-full" maxlength="120" autocomplete="off" formControlName="query" />
            </div>
            <div class="flex flex-col gap-2">
                <span id="lookup-type-label" class="font-medium">Tipo</span>
                <p-select-button ariaLabelledBy="lookup-type-label" formControlName="type" optionLabel="label" optionValue="value" [options]="assetTypes" [unselectable]="true" />
            </div>

            <p role="status" class="m-0 text-sm text-muted-color">{{ status() }}</p>

            @if (unavailable()) {
                <p-message severity="warn" text="Não foi possível consultar o provedor agora. Tente de novo ou preencha os dados manualmente." />
            }

            @if (candidates().length) {
                <ul class="list-none m-0 p-0 flex flex-col gap-1" aria-label="Ativos encontrados">
                    @for (candidate of candidates(); track candidate.symbol) {
                        <li>
                            <button type="button" class="w-full flex items-center justify-between gap-4 p-3 text-left text-color bg-transparent border-0 rounded-border cursor-pointer hover:bg-surface-100 dark:hover:bg-surface-800 focus-visible:outline-2 focus-visible:outline-primary" (click)="choose(candidate)">
                                <span class="flex flex-col min-w-0">
                                    <span class="font-semibold">{{ candidate.symbol }}</span>
                                    <span class="text-sm text-muted-color truncate">{{ candidate.name }}</span>
                                </span>
                                <span class="shrink-0 font-medium">
                                    @if (candidate.price !== null) {
                                        {{ candidate.price | currency: 'BRL' : 'symbol' : '1.2-8' }}
                                    } @else {
                                        <span class="font-normal text-sm text-muted-color">Sem cotação</span>
                                    }
                                </span>
                            </button>
                        </li>
                    }
                </ul>
            }
        </form>
    </p-dialog>`
})
export class AssetLookupDialog {
    private readonly wallet = inject(WalletService);

    readonly visible = model(false);
    readonly initialQuery = input('');
    readonly initialType = input<AssetType>('STOCK');
    readonly chosen = output<AssetCandidate>();
    readonly closed = output<void>();

    protected readonly assetTypes: { label: string; value: AssetType }[] = [
        { label: 'Ação', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    protected readonly form = inject(FormBuilder).nonNullable.group({
        type: ['STOCK' as AssetType],
        query: ['']
    });
    private readonly formValue = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), { initialValue: this.form.getRawValue() });

    private readonly searching = signal(false);
    /** Nulo enquanto não há o que procurar. */
    private readonly result = signal<AssetLookup | null>(null);

    protected readonly candidates = computed(() => this.result()?.candidates ?? []);
    protected readonly unavailable = computed(() => this.result()?.available === false);

    protected readonly status = computed(() => {
        const result = this.result();
        if (this.searching()) {
            return 'Buscando...';
        }
        if (!result) {
            return `Digite ao menos ${MIN_QUERY_LENGTH} caracteres.`;
        }
        if (!result.available) {
            return '';
        }
        const count = result.candidates.length;
        if (!count) {
            // A cotação das criptos é em reais: um par em outra moeda ficaria sem preço na carteira.
            return this.formValue().type === 'CRYPTO' ? 'Nenhum ativo encontrado. Só aparecem as criptos negociadas em reais.' : 'Nenhum ativo encontrado.';
        }
        return `${count === 1 ? '1 ativo encontrado' : `${count} ativos encontrados`}. Escolha um para preencher os dados.`;
    });

    constructor() {
        this.form.valueChanges
            .pipe(
                map(() => this.form.getRawValue()),
                tap(({ query }) => this.searching.set(query.trim().length >= MIN_QUERY_LENGTH)),
                debounceTime(SEARCH_DELAY_MS),
                switchMap(({ query, type }) => {
                    const term = query.trim();
                    return term.length < MIN_QUERY_LENGTH ? of(null) : this.wallet.lookupAssets(term, type).pipe(catchError(() => of<AssetLookup>({ available: false, candidates: [] })));
                }),
                takeUntilDestroyed()
            )
            .subscribe((result) => {
                this.result.set(result);
                this.searching.set(false);
            });

        effect(() => {
            if (this.visible()) {
                const query = this.initialQuery();
                const type = this.initialType();
                untracked(() => {
                    this.result.set(null);
                    this.form.reset({ query, type });
                });
            }
        });
    }

    protected choose(candidate: AssetCandidate): void {
        this.chosen.emit(candidate);
        this.visible.set(false);
    }
}
