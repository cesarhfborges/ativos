import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { StyleClassModule } from 'primeng/styleclass';
import { errorMessage } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { LayoutService } from '@/app/layout/service/layout.service';

@Component({
    selector: 'app-topbar',
    imports: [RouterLink, StyleClassModule],
    template: `<header class="layout-topbar">
        <div class="layout-topbar-logo-container">
            <button type="button" class="layout-menu-button layout-topbar-action" aria-label="Alternar menu de navegação" [attr.aria-expanded]="layoutService.isMenuVisible()" (click)="layoutService.toggleMenu()">
                <i class="pi pi-bars" aria-hidden="true"></i>
            </button>
            <a class="layout-topbar-logo" routerLink="/">
                <i class="pi pi-chart-line text-primary text-2xl!" aria-hidden="true"></i>
                <span>Ativos</span>
            </a>
        </div>

        <div class="layout-topbar-actions">
            <div class="layout-config-menu">
                <button type="button" class="layout-topbar-action" [attr.aria-label]="layoutService.isDarkTheme() ? 'Usar tema claro' : 'Usar tema escuro'" (click)="layoutService.toggleDarkTheme()">
                    <i class="pi" [class.pi-moon]="layoutService.isDarkTheme()" [class.pi-sun]="!layoutService.isDarkTheme()" aria-hidden="true"></i>
                </button>
            </div>

            <button
                type="button"
                class="layout-topbar-menu-button layout-topbar-action"
                aria-label="Abrir menu da conta"
                pStyleClass="@next"
                enterFromClass="hidden"
                enterActiveClass="animate-scalein"
                leaveToClass="hidden"
                leaveActiveClass="animate-fadeout"
                [hideOnOutsideClick]="true"
            >
                <i class="pi pi-ellipsis-v" aria-hidden="true"></i>
            </button>

            <div class="layout-topbar-menu hidden lg:block">
                <div class="layout-topbar-menu-content items-center">
                    <span class="flex items-center gap-2 px-2 text-color" [attr.title]="auth.user()?.email">
                        <i class="pi pi-user" aria-hidden="true"></i>
                        {{ auth.user()?.name }}
                    </span>
                    <button type="button" class="layout-topbar-action" aria-label="Sair" title="Sair" [disabled]="loggingOut()" (click)="logout()">
                        <i class="pi pi-sign-out" aria-hidden="true"></i>
                        <span>Sair</span>
                    </button>
                </div>
            </div>
        </div>
    </header>`
})
export class AppTopbar {
    protected readonly layoutService = inject(LayoutService);
    protected readonly auth = inject(AuthService);
    private readonly router = inject(Router);
    private readonly messages = inject(MessageService);

    protected readonly loggingOut = signal(false);

    protected logout(): void {
        this.loggingOut.set(true);
        this.auth.logout().subscribe({
            next: () => void this.router.navigate(['/auth/login']),
            error: (error: unknown) => {
                this.loggingOut.set(false);
                this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível sair', detail: errorMessage(error) });
            }
        });
    }
}
