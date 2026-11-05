import { Component, DestroyRef, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LayoutService } from '@/app/layout/service/layout.service';
import { AppFooter } from './app.footer';
import { AppSidebar } from './app.sidebar';
import { AppTopbar } from './app.topbar';

@Component({
    selector: 'app-layout',
    imports: [AppFooter, AppSidebar, AppTopbar, RouterOutlet],
    host: { '(document:keydown.escape)': 'layoutService.closeMobileMenu()' },
    template: `<div class="layout-wrapper layout-static" [class.layout-static-inactive]="layoutService.desktopMenuCollapsed()" [class.layout-mobile-active]="layoutService.mobileMenuOpen()">
        <app-topbar />
        <app-sidebar />
        <div class="layout-main-container">
            <main class="layout-main">
                <router-outlet />
            </main>
            <app-footer />
        </div>
        <div class="layout-mask" (click)="layoutService.closeMobileMenu()"></div>
    </div>`
})
export class AppLayout {
    protected readonly layoutService = inject(LayoutService);

    constructor() {
        inject(Router)
            .events.pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed(inject(DestroyRef))
            )
            .subscribe(() => this.layoutService.closeMobileMenu());

        effect(() => {
            document.body.classList.toggle('blocked-scroll', this.layoutService.mobileMenuOpen());
        });
    }
}
