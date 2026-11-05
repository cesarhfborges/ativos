import { computed, effect, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutService {
    private readonly desktopQuery = window.matchMedia('(min-width: 992px)');
    private readonly isDesktop = signal(this.desktopQuery.matches);

    readonly isDarkTheme = signal(false);
    /** No desktop, o menu fica aberto até o usuário recolher; no celular, fica fechado até ele abrir. */
    readonly desktopMenuCollapsed = signal(false);
    readonly mobileMenuOpen = signal(false);

    readonly isMenuVisible = computed(() => (this.isDesktop() ? !this.desktopMenuCollapsed() : this.mobileMenuOpen()));

    constructor() {
        this.desktopQuery.addEventListener('change', (event) => {
            this.isDesktop.set(event.matches);
            this.mobileMenuOpen.set(false);
        });

        effect(() => {
            document.documentElement.classList.toggle('app-dark', this.isDarkTheme());
        });
    }

    toggleDarkTheme(): void {
        this.isDarkTheme.update((dark) => !dark);
    }

    toggleMenu(): void {
        if (this.isDesktop()) {
            this.desktopMenuCollapsed.update((collapsed) => !collapsed);
        } else {
            this.mobileMenuOpen.update((open) => !open);
        }
    }

    closeMobileMenu(): void {
        this.mobileMenuOpen.set(false);
    }
}
