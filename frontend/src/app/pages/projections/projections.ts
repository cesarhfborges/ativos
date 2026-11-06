import { CurrencyPipe, DecimalPipe, formatDate, formatPercent } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, EMPTY, forkJoin, switchMap, tap } from 'rxjs';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { Scenario, SimulationResponse } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { PredictionService } from '@/app/core/prediction.service';
import { ProfitLoss } from '@/app/shared/profit-loss';
import { ProjectionChart, SCENARIO_LABELS } from './components/projection-chart';

const SCENARIO_ORDER: Scenario[] = ['PESSIMISTIC', 'REALISTIC', 'OPTIMISTIC'];
const SCENARIO_ICONS: Record<Scenario, string> = { PESSIMISTIC: 'pi pi-arrow-down-right', REALISTIC: 'pi pi-arrow-right', OPTIMISTIC: 'pi pi-arrow-up-right' };

@Component({
    selector: 'app-projections',
    imports: [CurrencyPipe, DecimalPaste, DecimalPipe, FormsModule, InputNumberModule, MessageModule, ProfitLoss, ProjectionChart, RouterLink, SelectButtonModule, SkeletonModule],
    template: `
        <div class="flex flex-wrap items-end justify-between gap-6 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Projeções</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Simule como sua carteira pode evoluir.</p>
            </div>
            <div class="flex flex-wrap items-end gap-4">
                <div class="flex flex-col gap-2">
                    <span id="projection-months-label" class="font-medium">Horizonte</span>
                    <p-selectbutton ariaLabelledBy="projection-months-label" optionLabel="label" optionValue="value" [options]="horizons" [unselectable]="true" [(ngModel)]="months" />
                </div>
                <div class="flex flex-col gap-2">
                    <label for="projection-contribution" class="font-medium">Compras por mês</label>
                    <p-inputnumber appDecimalPaste inputId="projection-contribution" mode="currency" currency="BRL" locale="pt-BR" placeholder="R$ 0,00" [min]="0" [maxFractionDigits]="2" [(ngModel)]="contribution" />
                </div>
            </div>
        </div>

        @if (disabled()) {
            <p-message severity="info" text="O módulo de projeções está desativado neste ambiente." />
        } @else if (failure(); as message) {
            <p-message severity="error" [text]="message" />
        } @else if (isEmpty()) {
            <div class="card mb-0! text-center py-12">
                <i class="pi pi-chart-line text-primary text-4xl! mb-4" aria-hidden="true"></i>
                <h2 class="text-xl! font-semibold mt-0! mb-2!">Ainda não há o que projetar</h2>
                <p class="text-muted-color mt-0! mb-0!">
                    <a routerLink="/carteira" class="text-primary font-medium underline">Registre suas compras na Carteira</a>
                    ou informe um valor em "Compras por mês" para simular.
                </p>
            </div>
        } @else if (scenarios(); as all) {
            <div class="flex flex-col gap-8" [attr.aria-busy]="loading()">
                <section aria-label="Cenários">
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                        @for (scenario of scenarioOrder; track scenario) {
                            <button
                                type="button"
                                class="card mb-0! text-left cursor-pointer border-2 transition-colors text-color"
                                [class.border-primary]="selected() === scenario"
                                [class.border-transparent]="selected() !== scenario"
                                [attr.aria-pressed]="selected() === scenario"
                                (click)="selected.set(scenario)"
                            >
                                <span class="flex items-center justify-between gap-2 font-medium mb-3">
                                    <span><i class="mr-2 {{ scenarioIcons[scenario] }}" aria-hidden="true"></i>{{ labels[scenario] }}</span>
                                    @if (selected() === scenario) {
                                        <i class="pi pi-check-circle text-primary" aria-hidden="true"></i>
                                    }
                                </span>
                                <span class="block text-2xl font-medium mb-1">{{ finalOf(scenario).expected | currency: 'BRL' : 'symbol' : '1.0-0' }}</span>
                                <span class="block text-muted-color text-sm">Entre {{ finalOf(scenario).lower | currency: 'BRL' : 'symbol' : '1.0-0' }} e {{ finalOf(scenario).upper | currency: 'BRL' : 'symbol' : '1.0-0' }}</span>
                            </button>
                        }
                    </div>
                </section>

                @if (active(); as data) {
                    <section class="card mb-0!" aria-labelledby="projection-summary">
                        <h2 id="projection-summary" class="text-xl! font-semibold mt-0! mb-4!">{{ headline() }}</h2>
                        <dl class="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-0 mb-6">
                            <div>
                                <dt class="text-muted-color text-sm">Hoje</dt>
                                <dd class="m-0 font-medium text-lg">{{ data.currentValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</dd>
                            </div>
                            <div>
                                <dt class="text-muted-color text-sm">Novas compras</dt>
                                <dd class="m-0 font-medium text-lg">{{ data.totalContributions | currency: 'BRL' : 'symbol' : '1.2-2' }}</dd>
                            </div>
                            <div>
                                <dt class="text-muted-color text-sm">Rendimento estimado</dt>
                                <dd class="m-0 text-lg"><app-profit-loss [value]="estimatedGain()" /></dd>
                            </div>
                            <div>
                                <dt class="text-muted-color text-sm">Retorno anual do cenário</dt>
                                <dd class="m-0 font-medium text-lg">{{ data.scenarioReturn * 100 | number: '1.1-1' }}%</dd>
                            </div>
                        </dl>
                        @if (initialDrop() > 0) {
                            <p class="text-muted-color mt-0! mb-4!">
                                <i class="pi pi-info-circle mr-1" aria-hidden="true"></i>Este cenário começa com uma queda imediata de {{ initialDrop() | currency: 'BRL' : 'symbol' : '1.2-2' }} na classe de maior risco da carteira.
                            </p>
                        }

                        <app-projection-chart [scenarios]="all" [selected]="selected()" />

                        <details class="mt-4">
                            <summary class="cursor-pointer text-muted-color">Ver mês a mês</summary>
                            <div class="stack-table stack-28 mt-2">
                                <table class="w-full">
                                    <thead>
                                        <tr>
                                            <th scope="col" class="py-1 text-left">Mês</th>
                                            <th scope="col" class="py-1 text-right">Limite inferior</th>
                                            <th scope="col" class="py-1 text-right">Esperado</th>
                                            <th scope="col" class="py-1 text-right">Limite superior</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        @for (point of data.projection; track point.date) {
                                            <tr>
                                                <td class="py-1 stack-full font-medium">{{ monthLabel(point.date) }}</td>
                                                <td class="py-1 text-right" data-label="Limite inferior">{{ point.lower | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                                                <td class="py-1 text-right font-medium" data-label="Esperado">{{ point.expected | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                                                <td class="py-1 text-right" data-label="Limite superior">{{ point.upper | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                                            </tr>
                                        }
                                    </tbody>
                                </table>
                            </div>
                        </details>
                    </section>

                    <div class="grid grid-cols-12 gap-4 sm:gap-8">
                        <section class="col-span-12 lg:col-span-7 card mb-0!" aria-labelledby="projection-risk-title">
                            <h2 id="projection-risk-title" class="text-xl! font-semibold mt-0! mb-4!">Riscos da carteira</h2>
                            <ul class="list-none m-0 p-0 flex flex-col gap-4">
                                @for (alert of data.alerts; track alert.title) {
                                    <li class="flex gap-3">
                                        <i class="mt-1 {{ alert.severity === 'WARNING' ? 'pi pi-exclamation-triangle text-yellow-800 dark:text-yellow-400' : 'pi pi-check-circle text-primary' }}" aria-hidden="true"></i>
                                        <div>
                                            <div class="font-medium">{{ alert.title }}</div>
                                            <div>{{ alert.message }}</div>
                                            @if (alert.suggestion) {
                                                <div class="text-muted-color mt-1">{{ alert.suggestion }}</div>
                                            }
                                        </div>
                                    </li>
                                }
                            </ul>
                        </section>

                        <section class="col-span-12 lg:col-span-5 card mb-0!" aria-labelledby="projection-assumptions-title">
                            <h2 id="projection-assumptions-title" class="text-xl! font-semibold mt-0! mb-4!">De onde vêm os números</h2>
                            <p class="mt-0! mb-4!">{{ assumptionsText() }}</p>
                            <details>
                                <summary class="cursor-pointer text-muted-color">Ver premissas</summary>
                                <dl class="grid grid-cols-2 gap-y-2 mt-3 mb-0">
                                    <dt>Retorno anual base</dt>
                                    <dd class="m-0 text-right font-medium">{{ data.assumptions.annualReturn * 100 | number: '1.1-1' }}%</dd>
                                    <dt>Volatilidade anual</dt>
                                    <dd class="m-0 text-right font-medium">{{ data.assumptions.annualVolatility * 100 | number: '1.1-1' }}%</dd>
                                    <dt>Faixa provável</dt>
                                    <dd class="m-0 text-right font-medium">{{ data.assumptions.confidenceLevel * 100 | number: '1.0-0' }}% de confiança</dd>
                                    <dt>Histórico disponível</dt>
                                    <dd class="m-0 text-right font-medium">{{ data.assumptions.historyDays }} dias</dd>
                                </dl>
                            </details>
                            <p class="text-muted-color text-sm mt-4! mb-0!">Simulação com premissas simplificadas. Não é previsão nem recomendação de investimento.</p>
                        </section>
                    </div>
                }
            </div>
        } @else {
            <div class="flex flex-col gap-8" aria-busy="true" aria-label="Calculando a simulação">
                <p-skeleton height="8rem" />
                <p-skeleton height="24rem" />
            </div>
        }
    `
})
export class Projections {
    private readonly predictions = inject(PredictionService);

    protected readonly labels = SCENARIO_LABELS;
    protected readonly scenarioOrder = SCENARIO_ORDER;
    protected readonly scenarioIcons = SCENARIO_ICONS;
    protected readonly horizons = [
        { label: '6 meses', value: 6 },
        { label: '1 ano', value: 12 },
        { label: '2 anos', value: 24 },
        { label: '5 anos', value: 60 }
    ];

    protected readonly months = signal(12);
    protected readonly contribution = signal<number | null>(null);
    protected readonly selected = signal<Scenario>('REALISTIC');

    protected readonly scenarios = signal<Record<Scenario, SimulationResponse> | null>(null);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);
    protected readonly disabled = signal(false);

    protected readonly active = computed(() => this.scenarios()?.[this.selected()] ?? null);
    protected readonly isEmpty = computed(() => {
        const data = this.active();
        return !!data && data.currentValue === 0 && Number(data.monthlyContribution) === 0;
    });

    protected readonly headline = computed(() => {
        const horizon = this.horizons.find((option) => option.value === this.active()?.months)?.label ?? '';
        const last = this.active()?.projection.at(-1);
        const value = last ? last.expected.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }) : '';
        return `Em ${horizon}, sua carteira pode chegar a ${value} no cenário ${SCENARIO_LABELS[this.selected()].toLowerCase()}`;
    });

    /** Inclui a queda inicial do cenário pessimista: é a diferença entre o fim e o que foi colocado. */
    protected readonly estimatedGain = computed(() => {
        const data = this.active();
        return data ? (data.projection.at(-1)?.expected ?? 0) - data.currentValue - data.totalContributions : 0;
    });
    protected readonly initialDrop = computed(() => {
        const data = this.active();
        return data ? data.currentValue - (data.projection[0]?.expected ?? data.currentValue) : 0;
    });

    protected readonly assumptionsText = computed(() => {
        const assumptions = this.active()?.assumptions;
        if (!assumptions) {
            return '';
        }
        switch (assumptions.source) {
            case 'MARKET':
                return `Tendência e volatilidade medidas em ${assumptions.historyDays} dias de cotações dos ativos que você tem hoje.`;
            case 'BLENDED':
                return `${formatPercent(assumptions.coverage, 'pt-BR', '1.0-0')} da carteira foi medido em ${assumptions.historyDays} dias de cotações. O restante ainda tem pouco histórico e usa valores de referência configurados, que não vêm do mercado.`;
            default:
                return 'Ainda não há histórico suficiente de cotações dos seus ativos, então a simulação usa valores de referência configurados, que não vêm do mercado.';
        }
    });

    constructor() {
        const params = computed(() => ({ months: this.months(), monthlyContribution: this.contribution() ?? 0 }));
        toObservable(params)
            .pipe(
                debounceTime(300),
                tap(() => this.loading.set(true)),
                // switchMap descarta a resposta antiga se o usuário mudar os controles antes de ela chegar.
                switchMap((request) =>
                    forkJoin({
                        PESSIMISTIC: this.predictions.simulate({ ...request, scenario: 'PESSIMISTIC' }),
                        REALISTIC: this.predictions.simulate({ ...request, scenario: 'REALISTIC' }),
                        OPTIMISTIC: this.predictions.simulate({ ...request, scenario: 'OPTIMISTIC' })
                    }).pipe(
                        catchError((error: unknown) => {
                            this.disabled.set(error instanceof HttpErrorResponse && error.status === 404);
                            this.failure.set(errorMessage(error));
                            this.loading.set(false);
                            return EMPTY;
                        })
                    )
                ),
                takeUntilDestroyed()
            )
            .subscribe((scenarios) => {
                this.scenarios.set(scenarios);
                this.failure.set(null);
                this.loading.set(false);
            });
    }

    protected finalOf(scenario: Scenario): SimulationResponse['projection'][number] {
        const projection = this.scenarios()?.[scenario].projection ?? [];
        return projection[projection.length - 1];
    }

    protected monthLabel(date: string): string {
        return formatDate(`${date}T12:00:00Z`, 'MM/yyyy', 'pt-BR', 'UTC');
    }
}
