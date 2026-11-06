import { Component, effect, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ConfirmRequest, ConfirmService } from '@/app/core/confirm.service';

/** Usa o p-dialog porque o p-confirmdialog não tem nome acessível nem fecha com Esc. */
@Component({
    selector: 'app-confirm-dialog',
    imports: [ButtonModule, DialogModule],
    template: `<p-dialog
        styleClass="app-confirm"
        closeAriaLabel="Fechar"
        [header]="request()?.header"
        [modal]="true"
        [draggable]="false"
        [resizable]="false"
        [style]="{ width: '28rem' }"
        [breakpoints]="{ '640px': '95vw' }"
        [(visible)]="visible"
        (onHide)="onHide()"
    >
        <div class="flex items-start gap-3">
            <i class="text-2xl! {{ request()?.danger ? 'pi pi-exclamation-triangle text-red-700 dark:text-red-400' : 'pi pi-question-circle text-primary' }}" aria-hidden="true"></i>
            <p class="m-0!">{{ request()?.message }}</p>
        </div>
        <ng-template #footer>
            <!-- Começar em "Cancelar" evita que um segundo Enter confirme sem querer. -->
            <button pButton type="button" severity="secondary" [text]="true" autofocus (click)="visible.set(false)">Cancelar</button>
            <button pButton type="button" [severity]="request()?.danger ? 'danger' : null" (click)="accept()">{{ request()?.acceptLabel }}</button>
        </ng-template>
    </p-dialog>`
})
export class ConfirmDialog {
    private readonly service = inject(ConfirmService);
    private accepted = false;

    protected readonly request = this.service.request;
    protected readonly visible = signal(false);

    constructor() {
        effect(() => {
            if (this.request()) {
                this.accepted = false;
                this.visible.set(true);
            }
        });
    }

    protected accept(): void {
        this.accepted = true;
        this.visible.set(false);
        (this.request() as ConfirmRequest).onAccept();
    }

    protected onHide(): void {
        if (!this.accepted) {
            this.request()?.opener?.focus();
        }
    }
}
