import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialog } from './app/shared/confirm-dialog';

@Component({
    selector: 'app-root',
    imports: [ConfirmDialog, RouterOutlet, ToastModule],
    template: `<p-toast [life]="5000" />
        <app-confirm-dialog />
        <router-outlet />`
})
export class AppComponent {}
