import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WatchlistItem } from '@/app/core/api.models';
import { reachedTargets } from '@/app/core/watchlist';

const MAX_SHOWN = 6;

/** Os alvos do plano que o preço já alcançou, para o usuário ver isso assim que entra. */
@Component({
    selector: 'app-reached-targets',
    imports: [CurrencyPipe, RouterLink],
    template: `<div class="card mb-0!">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
            <h2 class="text-xl! font-semibold m-0!">Alvos atingidos</h2>
            <a routerLink="/acompanhamento" class="underline font-medium">Ver acompanhamento</a>
        </div>
        @if (items().length === 0) {
            <p class="text-muted-color m-0!">Você ainda não acompanha nenhum ativo. Defina seus alvos de compra e venda no Acompanhamento.</p>
        } @else if (reached().length === 0) {
            <p class="text-muted-color m-0!">Nenhum alvo atingido com as cotações atuais.</p>
        } @else {
            <ul class="list-none m-0 p-0 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                @for (entry of shown(); track entry.item.id + entry.target.kind) {
                    <li class="flex items-start gap-2">
                        <i class="pi pi-flag-fill text-primary mt-1" aria-hidden="true"></i>
                        <span>
                            <span class="font-semibold">{{ entry.item.symbol }}</span>
                            <span class="block text-muted-color text-sm">{{ entry.target.label }} {{ entry.target.price | currency: 'BRL' : 'symbol' : '1.2-8' }}; cotação {{ entry.item.currentPrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</span>
                        </span>
                    </li>
                }
            </ul>
            @if (hidden() > 0) {
                <p class="text-muted-color mt-4 mb-0!">E mais {{ hidden() }} no Acompanhamento.</p>
            }
        }
    </div>`
})
export class ReachedTargets {
    readonly items = input.required<WatchlistItem[]>();

    protected readonly reached = computed(() => reachedTargets(this.items()));
    protected readonly shown = computed(() => this.reached().slice(0, MAX_SHOWN));
    protected readonly hidden = computed(() => this.reached().length - this.shown().length);
}
