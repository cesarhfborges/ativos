import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthCard } from '../auth/auth-card';

@Component({
    selector: 'app-notfound',
    imports: [AuthCard, ButtonModule, RouterLink],
    template: `<app-auth-card title="Página não encontrada" subtitle="O endereço acessado não existe ou foi movido.">
        <a pButton routerLink="/" class="w-full justify-center">Voltar ao painel</a>
    </app-auth-card>`
})
export class Notfound {}
