import { Component, inject } from '@angular/core';
import { LayoutService } from '@/app/layout/service/layout.service';
import { AppMenu } from './app.menu';

@Component({
    selector: 'app-sidebar',
    imports: [AppMenu],
    // Fechado, o menu fica só fora da tela: sem o inert, seus links continuariam recebendo foco pelo teclado.
    template: `<nav class="layout-sidebar" aria-label="Navegação principal" [inert]="!layoutService.isMenuVisible()">
        <app-menu />
    </nav>`
})
export class AppSidebar {
    protected readonly layoutService = inject(LayoutService);
}
