import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

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
    </ul>`
})
export class AppMenu {
    protected readonly items = [
        { label: 'Painel', icon: 'pi pi-fw pi-home', link: '/' },
        { label: 'Carteira', icon: 'pi pi-fw pi-wallet', link: '/carteira' },
        { label: 'Extrato', icon: 'pi pi-fw pi-list', link: '/extrato' },
        { label: 'Projeções', icon: 'pi pi-fw pi-chart-line', link: '/projecoes' },
        { label: 'Meu perfil', icon: 'pi pi-fw pi-user', link: '/perfil' }
    ];
}
