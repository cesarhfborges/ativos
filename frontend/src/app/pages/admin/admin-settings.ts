import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AdminService } from '@/app/core/admin.service';
import { AdminSetting, AdminSettings } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';

/** Valor como aparece no campo: percentuais em pontos, com arredondamento para esconder o ruído do ponto flutuante. */
function toDisplay(setting: Pick<AdminSetting, 'type'>, value: number): number {
    return setting.type === 'PERCENT' ? Math.round(value * 1e6) / 1e4 : value;
}

/** Valor como o backend guarda: percentuais em fração. */
function toStored(setting: AdminSetting, value: number | boolean): string {
    if (typeof value === 'boolean') {
        return String(value);
    }
    return setting.type === 'PERCENT' ? String(Math.round(value * 1e4) / 1e6) : String(value);
}

function displayValue(setting: AdminSetting): number | boolean {
    return setting.type === 'BOOLEAN' ? setting.value === 'true' : toDisplay(setting, Number(setting.value));
}

@Component({
    selector: 'app-admin-settings',
    imports: [ButtonModule, FormsModule, InputNumberModule, MessageModule, SkeletonModule, ToggleSwitchModule],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Configurações</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">As alterações valem na hora para todos os usuários.</p>
            </div>
            <p-button label="Salvar alterações" icon="pi pi-check" [disabled]="changedCount() === 0" [loading]="saving()" (onClick)="save()" />
        </div>

        @if (failure(); as message) {
            <p-message severity="error" [text]="message" styleClass="mb-6" />
        }

        @if (data(); as loaded) {
            <div class="flex flex-col gap-8">
                @for (group of groups(); track group.name) {
                    <section class="card mb-0!" [attr.aria-label]="group.name">
                        <h2 class="text-xl! font-semibold mt-0! mb-6!">{{ group.name }}</h2>
                        <div class="flex flex-col gap-6">
                            @for (setting of group.settings; track setting.key) {
                                <div class="flex flex-wrap items-start justify-between gap-4">
                                    <div class="flex-1 min-w-64">
                                        <label class="font-medium" [attr.for]="setting.key">{{ setting.label }}</label>
                                        <div class="text-muted-color text-sm">{{ setting.description }}</div>
                                        <div class="text-muted-color text-sm">Padrão: {{ defaultLabel(setting) }}</div>
                                    </div>
                                    <div class="flex items-center gap-2">
                                        @if (setting.overridden) {
                                            <p-button label="Restaurar padrão" size="small" severity="secondary" [text]="true" [ariaLabel]="'Restaurar padrão de ' + setting.label" (onClick)="restore(setting)" />
                                        }
                                        @if (setting.type === 'BOOLEAN') {
                                            <p-toggleswitch [inputId]="setting.key" [ngModel]="draftBoolean(setting)" (ngModelChange)="change(setting, $event)" />
                                        } @else {
                                            <p-inputnumber
                                                locale="pt-BR"
                                                [inputId]="setting.key"
                                                [suffix]="setting.type === 'PERCENT' ? '%' : ''"
                                                [min]="limit(setting, setting.min)"
                                                [max]="limit(setting, setting.max)"
                                                [maxFractionDigits]="setting.type === 'PERCENT' ? 4 : 0"
                                                [ngModel]="draftNumber(setting)"
                                                (ngModelChange)="change(setting, $event)"
                                            />
                                        }
                                    </div>
                                </div>
                            }
                        </div>
                    </section>
                }

                <section class="card mb-0!" aria-labelledby="system-info-title">
                    <h2 id="system-info-title" class="text-xl! font-semibold mt-0! mb-1!">Ambiente</h2>
                    <p class="text-muted-color mt-0! mb-4!">Definido nas variáveis de ambiente do servidor; não muda por aqui.</p>
                    <dl class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 m-0">
                        <div>
                            <dt class="text-muted-color text-sm">Provedor de cripto</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.cryptoProvider || 'Desligado' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Provedor de ações</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.stockProvider || 'Desligado' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Token da Brapi</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.brapiTokenConfigured ? 'Definido' : 'Não definido' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Agenda da coleta (cron)</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.collectionCron }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Coleta ao iniciar</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.collectOnStartup ? 'Sim' : 'Não' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted-color text-sm">Módulo de projeções</dt>
                            <dd class="m-0 font-medium">{{ loaded.system.predictiveEnabled ? 'Ligado' : 'Desligado' }}</dd>
                        </div>
                    </dl>
                </section>
            </div>
        } @else if (!failure()) {
            <p-skeleton height="24rem" />
        }
    `
})
export class AdminSettingsPage {
    private readonly admin = inject(AdminService);
    private readonly messages = inject(MessageService);

    protected readonly data = signal<AdminSettings | null>(null);
    protected readonly failure = signal<string | null>(null);
    protected readonly saving = signal(false);
    /** O que foi alterado na tela e ainda não salvo. */
    private readonly drafts = signal<Record<string, number | boolean>>({});

    protected readonly groups = computed(() => {
        const groups: { name: string; settings: AdminSetting[] }[] = [];
        for (const setting of this.data()?.settings ?? []) {
            const group = groups.find((candidate) => candidate.name === setting.group);
            if (group) {
                group.settings.push(setting);
            } else {
                groups.push({ name: setting.group, settings: [setting] });
            }
        }
        return groups;
    });

    protected readonly changedCount = computed(() => Object.keys(this.drafts()).length);

    constructor() {
        this.admin.settings().subscribe({
            next: (data) => this.data.set(data),
            error: (error: unknown) => this.failure.set(errorMessage(error))
        });
    }

    protected draftBoolean(setting: AdminSetting): boolean {
        return (this.drafts()[setting.key] ?? displayValue(setting)) as boolean;
    }

    protected draftNumber(setting: AdminSetting): number {
        return (this.drafts()[setting.key] ?? displayValue(setting)) as number;
    }

    protected limit(setting: AdminSetting, value: number | null): number | undefined {
        return value === null ? undefined : toDisplay(setting, value);
    }

    protected defaultLabel(setting: AdminSetting): string {
        if (setting.type === 'BOOLEAN') {
            return setting.defaultValue === 'true' ? 'ligado' : 'desligado';
        }
        const value = toDisplay(setting, Number(setting.defaultValue)).toLocaleString('pt-BR', { maximumFractionDigits: 4 });
        return setting.type === 'PERCENT' ? `${value}%` : value;
    }

    /** Campo vazio ou de volta ao valor salvo não conta como alteração. */
    protected change(setting: AdminSetting, value: number | boolean | null): void {
        this.drafts.update((drafts) => {
            const { [setting.key]: _previous, ...others } = drafts;
            return value === null || value === displayValue(setting) ? others : { ...others, [setting.key]: value };
        });
    }

    protected save(): void {
        const settings = this.data()?.settings ?? [];
        const values = Object.fromEntries(Object.entries(this.drafts()).map(([key, value]) => [key, toStored(settings.find((setting) => setting.key === key)!, value)]));
        this.saving.set(true);
        this.failure.set(null);
        this.admin.updateSettings(values).subscribe({
            next: (data) => {
                this.apply(data);
                this.saving.set(false);
                this.messages.add({ severity: 'success', closable: false, summary: 'Configurações salvas' });
            },
            error: (error: unknown) => {
                this.saving.set(false);
                this.failure.set(errorMessage(error));
            }
        });
    }

    protected restore(setting: AdminSetting): void {
        this.admin.restoreSetting(setting.key).subscribe({
            next: (data) => {
                const { [setting.key]: _discarded, ...others } = this.drafts();
                this.drafts.set(others);
                this.data.set(data);
                this.messages.add({ severity: 'success', closable: false, summary: 'Valor padrão restaurado', detail: setting.label });
            },
            error: (error: unknown) => this.failure.set(errorMessage(error))
        });
    }

    private apply(data: AdminSettings): void {
        this.drafts.set({});
        this.data.set(data);
    }
}
