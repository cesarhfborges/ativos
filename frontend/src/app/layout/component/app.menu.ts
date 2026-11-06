import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '@/app/core/auth.service';

@Component({
    selector: 'app-menu',
    imports: [RouterLink, RouterLinkActive],
    template: `<ul class="layout-menu">
        <li class="layout-root-menuitem">
            <div class="layout-menuitem-root-text">Investimentos</div>
            <ul class="layout-root-submenulist">
                @for (item of items; track item.link) {
                    <li>
                        <a [routerLink]="item.link" routerLinkActive="active-route" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="{ exact: true }">
                            <i class="layout-menuitem-icon {{ item.icon }}" aria-hidden="true"></i>
                            <span class="layout-menuitem-text">{{ item.label }}</span>
                        </a>
                    </li>
                }
            </ul>
        </li>
        @if (auth.isAdmin()) {
            <li class="layout-root-menuitem">
                <div class="layout-menuitem-root-text">Administração</div>
                <ul class="layout-root-submenulist">
                    @for (item of adminItems; track item.link) {
                        <li>
                            <a [routerLink]="item.link" routerLinkActive="active-route" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="{ exact: true }">
                                <i class="layout-menuitem-icon {{ item.icon }}" aria-hidden="true"></i>
                                <span class="layout-menuitem-text">{{ item.label }}</span>
                            </a>
                        </li>
                    }
                </ul>
            </li>
        }
    </ul>`
})
export class AppMenu {
    protected readonly auth = inject(AuthService);

    protected readonly adminItems = [
        { label: 'Visão geral', icon: 'pi pi-fw pi-gauge', link: '/admin' },
        { label: 'Usuários', icon: 'pi pi-fw pi-users', link: '/admin/usuarios' },
        { label: 'Ativos', icon: 'pi pi-fw pi-database', link: '/admin/ativos' },
        { label: 'Configurações', icon: 'pi pi-fw pi-cog', link: '/admin/configuracoes' },
        { label: 'Auditoria', icon: 'pi pi-fw pi-history', link: '/admin/auditoria' }
    ];

    protected readonly items = [
        { label: 'Painel', icon: 'pi pi-fw pi-home', link: '/' },
        { label: 'Carteira', icon: 'pi pi-fw pi-wallet', link: '/carteira' },
        { label: 'Extrato', icon: 'pi pi-fw pi-list', link: '/extrato' },
        { label: 'Projeções', icon: 'pi pi-fw pi-chart-line', link: '/projecoes' },
        { label: 'Meu perfil', icon: 'pi pi-fw pi-user', link: '/perfil' }
    ];
}
