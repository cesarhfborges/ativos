import { Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AdminService } from '@/app/core/admin.service';
import { AssetType, CatalogAsset } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { ConfirmService } from '@/app/core/confirm.service';
import { applyFieldErrors, controlError } from '@/app/core/form-errors';

@Component({
    selector: 'app-asset-dialog',
    imports: [ButtonModule, DialogModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectButtonModule],
    template: `<p-dialog [header]="'Editar ' + (asset()?.symbol ?? '')" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '28rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible">
        <form id="asset-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }
            <div class="flex flex-col gap-2">
                <label for="asset-name" class="font-medium">Nome</label>
                <input pInputText id="asset-name" class="w-full" autocomplete="off" formControlName="name" [invalid]="!!error()" [attr.aria-invalid]="!!error()" />
                @if (error(); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="flex flex-col gap-2">
                <span id="asset-type-label" class="font-medium">Tipo</span>
                <p-select-button ariaLabelledBy="asset-type-label" formControlName="assetType" optionLabel="label" optionValue="value" [options]="types" [unselectable]="true" />
                <small class="text-muted-color">O tipo define em qual provedor o ativo é cotado.</small>
            </div>
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="asset-form" [loading]="submitting()">Salvar</button>
        </ng-template>
    </p-dialog>`
})
export class AssetDialog {
    private readonly admin = inject(AdminService);
    private readonly confirm = inject(ConfirmService);

    readonly visible = model(false);
    readonly asset = input<CatalogAsset | null>(null);
    readonly saved = output<CatalogAsset>();

    protected readonly types: { label: string; value: AssetType }[] = [
        { label: 'Ação', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    protected readonly form = inject(NonNullableFormBuilder).group({
        name: ['', [Validators.required, Validators.maxLength(120)]],
        assetType: ['STOCK' as AssetType, [Validators.required]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    constructor() {
        effect(() => {
            if (this.visible()) {
                const asset = this.asset();
                untracked(() => {
                    this.form.reset({ name: asset?.name ?? '', assetType: asset?.assetType ?? 'STOCK' });
                    this.failure.set(null);
                    this.submitting.set(false);
                });
            }
        });
    }

    protected error(): string | null {
        this.formEvents();
        return controlError(this.form.controls.name);
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const asset = this.asset();
        if (this.form.invalid || this.submitting() || !asset) {
            return;
        }
        if (this.form.getRawValue().assetType === asset.assetType) {
            this.send(asset);
            return;
        }
        this.confirm.ask(
            { header: 'Mudar o tipo do ativo', message: `Mudar o tipo de ${asset.symbol} para todos os usuários? Ele passará a ser cotado por outro provedor e será conferido novamente.`, acceptLabel: 'Mudar tipo' },
            () => this.send(asset)
        );
    }

    private send(asset: CatalogAsset): void {
        const { name, assetType } = this.form.getRawValue();
        this.submitting.set(true);
        this.failure.set(null);
        this.admin.updateAsset(asset.id, name, assetType).subscribe({
            next: (saved) => {
                this.submitting.set(false);
                this.visible.set(false);
                this.saved.emit(saved);
            },
            error: (error: unknown) => {
                this.submitting.set(false);
                if (!applyFieldErrors(this.form, fieldErrors(error))) {
                    this.failure.set(errorMessage(error));
                }
            }
        });
    }
}
