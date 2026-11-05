import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import { ApplicationConfig, inject, LOCALE_ID, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { appRoutes } from './app.routes';
import { authInterceptor } from './app/core/auth.interceptor';
import { AuthService } from './app/core/auth.service';

registerLocaleData(localePt);

/** Cores do tema claro que ficam abaixo do contraste AA de 4,5:1 nos tons padrão do Aura. */
const SEVERITY_COLORS = {
    info: { color: '{blue.700}' },
    success: { color: '{green.700}' },
    warn: { color: '{yellow.800}' },
    error: { color: '{red.700}' }
};
const withSimpleVariant = (tokens: { color: string }) => ({ ...tokens, simple: tokens });

const AtivosPreset = definePreset(Aura, {
    semantic: {
        colorScheme: {
            light: {
                primary: {
                    color: '{primary.700}',
                    contrastColor: '#ffffff',
                    hoverColor: '{primary.800}',
                    activeColor: '{primary.900}'
                }
            }
        }
    },
    components: {
        button: { colorScheme: { light: { outlined: { secondary: { color: '{surface.700}' } }, text: { secondary: { color: '{surface.700}' } } } } },
        toast: { colorScheme: { light: SEVERITY_COLORS } },
        message: {
            colorScheme: {
                light: {
                    info: withSimpleVariant(SEVERITY_COLORS.info),
                    success: withSimpleVariant(SEVERITY_COLORS.success),
                    warn: withSimpleVariant(SEVERITY_COLORS.warn),
                    error: withSimpleVariant(SEVERITY_COLORS.error)
                }
            }
        },
        togglebutton: { colorScheme: { light: { root: { color: '{surface.600}' } } } }
    }
});

export const appConfig: ApplicationConfig = {
    providers: [
        // A navegação inicial é a padrão, não a bloqueante: ela precisa esperar o initializer abaixo,
        // senão o guard roda antes de a sessão ser restaurada e todo F5 cai na tela de login.
        provideRouter(appRoutes, withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }), withComponentInputBinding()),
        provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
        provideZonelessChangeDetection(),
        provideAppInitializer(() => inject(AuthService).restoreSession()),
        providePrimeNG({ theme: { preset: AtivosPreset, options: { darkModeSelector: '.app-dark' } } }),
        MessageService,
        { provide: LOCALE_ID, useValue: 'pt-BR' }
    ]
};
