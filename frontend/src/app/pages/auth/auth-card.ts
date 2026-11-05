import { Component, input } from '@angular/core';
import { AppFloatingConfigurator } from '@/app/layout/component/app.floatingconfigurator';

/** Moldura das telas públicas: marca, título e o conteúdo projetado. */
@Component({
    selector: 'app-auth-card',
    imports: [AppFloatingConfigurator],
    template: `
        <app-floating-configurator />
        <main class="bg-surface-50 dark:bg-surface-950 flex items-center justify-center min-h-screen p-4">
            <div class="w-full max-w-lg bg-surface-0 dark:bg-surface-900 rounded-3xl border border-surface-200 dark:border-surface-700 py-12 px-6 sm:px-12">
                <div class="text-center mb-8">
                    <i class="pi pi-chart-line text-primary text-5xl! mb-4" aria-hidden="true"></i>
                    <h1 class="text-surface-900 dark:text-surface-0 text-3xl! font-medium mt-0! mb-2!">{{ title() }}</h1>
                    <p class="text-muted-color font-medium m-0!">{{ subtitle() }}</p>
                </div>
                <ng-content />
            </div>
        </main>
    `
})
export class AuthCard {
    readonly title = input.required<string>();
    readonly subtitle = input.required<string>();
}
