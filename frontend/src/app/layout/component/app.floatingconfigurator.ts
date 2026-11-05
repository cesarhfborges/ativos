import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { LayoutService } from '@/app/layout/service/layout.service';

/** Alternância de tema para as telas fora do layout principal, como login e registro. */
@Component({
    selector: 'app-floating-configurator',
    imports: [ButtonModule],
    template: `<div class="fixed top-8 right-8">
        <p-button
            type="button"
            severity="secondary"
            [rounded]="true"
            [icon]="layoutService.isDarkTheme() ? 'pi pi-moon' : 'pi pi-sun'"
            [ariaLabel]="layoutService.isDarkTheme() ? 'Usar tema claro' : 'Usar tema escuro'"
            (onClick)="layoutService.toggleDarkTheme()"
        />
    </div>`
})
export class AppFloatingConfigurator {
    protected readonly layoutService = inject(LayoutService);
}
