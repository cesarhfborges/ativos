import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AdminService } from '@/app/core/admin.service';
import { AdminUser } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { ConfirmService } from '@/app/core/confirm.service';
import { PasswordDialog } from './components/password-dialog';
import { UserDialog } from './components/user-dialog';

const PAGE_SIZE = 20;

@Component({
    selector: 'app-admin-users',
    imports: [ButtonModule, DatePipe, InputTextModule, MessageModule, PasswordDialog, TableModule, TagModule, UserDialog],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <h1 class="text-3xl! font-semibold m-0!">Usuários</h1>
            <div class="flex flex-wrap items-center gap-2">
                <input pInputText type="search" class="w-72 max-w-full" aria-label="Buscar por nome ou e-mail" placeholder="Buscar por nome ou e-mail" (input)="searches.next($any($event.target).value)" />
                <p-button label="Nova conta" icon="pi pi-plus" (onClick)="openCreate()" />
            </div>
        </div>

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
            }
            <div class="stack-table stack-66">
                <p-table dataKey="id" [value]="users()" [lazy]="true" [paginator]="true" [rows]="pageSize" [totalRecords]="total()" [loading]="loading()" (onLazyLoad)="onPage($event)">
                    <ng-template #header>
                        <tr>
                            <th scope="col">Nome</th>
                            <th scope="col">Papel</th>
                            <th scope="col">Situação</th>
                            <th scope="col">Último acesso</th>
                            <th scope="col" class="text-right!">Ações</th>
                        </tr>
                    </ng-template>
                    <ng-template #body let-user>
                        <tr>
                            <td class="stack-full">
                                <div class="font-medium">{{ user.name }}</div>
                                <div class="text-muted-color text-sm">{{ user.email }}</div>
                            </td>
                            <td data-label="Papel"><p-tag [value]="user.role === 'ADMIN' ? 'Administrador' : 'Usuário'" [severity]="user.role === 'ADMIN' ? 'info' : 'secondary'" /></td>
                            <td data-label="Situação"><p-tag [value]="user.active ? 'Ativa' : 'Desativada'" [severity]="user.active ? 'success' : 'danger'" /></td>
                            <td class="whitespace-nowrap" data-label="Último acesso">{{ user.lastLoginAt ? (user.lastLoginAt | date: 'short') : 'Nunca' }}</td>
                            <td class="stack-actions text-right whitespace-nowrap">
                                <p-button label="Editar" size="small" severity="secondary" [text]="true" [ariaLabel]="'Editar ' + user.name" (onClick)="openEdit(user)" />
                                <p-button label="Senha" size="small" severity="secondary" [text]="true" [ariaLabel]="'Definir nova senha de ' + user.name" (onClick)="openPassword(user)" />
                                <p-button label="Encerrar sessões" size="small" severity="secondary" [text]="true" [ariaLabel]="'Encerrar sessões de ' + user.name" (onClick)="endSessions(user)" />
                                <p-button
                                    size="small"
                                    [label]="user.active ? 'Desativar' : 'Ativar'"
                                    [severity]="user.active ? 'danger' : 'secondary'"
                                    [text]="true"
                                    [ariaLabel]="(user.active ? 'Desativar ' : 'Ativar ') + user.name"
                                    (onClick)="toggleActive(user)"
                                />
                            </td>
                        </tr>
                    </ng-template>
                    <ng-template #emptymessage>
                        <tr>
                            <td colspan="5" class="text-center text-muted-color">Nenhuma conta encontrada.</td>
                        </tr>
                    </ng-template>
                </p-table>
            </div>
        </div>

        <app-user-dialog [user]="editing()" [(visible)]="userDialogVisible" (saved)="onSaved($event)" />
        <app-password-dialog [user]="editing()" [(visible)]="passwordDialogVisible" (saved)="onPasswordSaved()" />
    `
})
export class AdminUsersPage {
    private readonly admin = inject(AdminService);
    private readonly auth = inject(AuthService);
    private readonly confirm = inject(ConfirmService);
    private readonly messages = inject(MessageService);

    protected readonly pageSize = PAGE_SIZE;
    protected readonly searches = new Subject<string>();

    protected readonly users = signal<AdminUser[]>([]);
    protected readonly total = signal(0);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);

    protected readonly editing = signal<AdminUser | null>(null);
    protected readonly userDialogVisible = signal(false);
    protected readonly passwordDialogVisible = signal(false);

    private query = '';
    private page = 0;

    constructor() {
        this.searches.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((query) => {
            this.query = query;
            this.page = 0;
            this.load();
        });
    }

    protected onPage(event: TableLazyLoadEvent): void {
        this.page = Math.floor((event.first ?? 0) / PAGE_SIZE);
        this.load();
    }

    protected openCreate(): void {
        this.editing.set(null);
        this.userDialogVisible.set(true);
    }

    protected openEdit(user: AdminUser): void {
        this.editing.set(user);
        this.userDialogVisible.set(true);
    }

    protected openPassword(user: AdminUser): void {
        this.editing.set(user);
        this.passwordDialogVisible.set(true);
    }

    protected onSaved(user: AdminUser): void {
        this.messages.add({ severity: 'success', closable: false, summary: 'Conta salva', detail: user.email });
        if (user.id === this.auth.user()?.id) {
            this.auth.updateName(user.name);
        }
        this.load();
    }

    protected onPasswordSaved(): void {
        this.messages.add({ severity: 'success', closable: false, summary: 'Senha definida', detail: 'As sessões da conta foram encerradas.' });
    }

    protected endSessions(user: AdminUser): void {
        this.confirm.ask(
            { header: 'Encerrar sessões', message: `Encerrar todas as sessões de ${user.email}? Será preciso entrar novamente.${this.selfWarning(user)}`, acceptLabel: 'Encerrar sessões' },
            () =>
                this.admin.endSessions(user.id).subscribe({
                    next: () => this.messages.add({ severity: 'success', closable: false, summary: 'Sessões encerradas', detail: user.email }),
                    error: (error: unknown) => this.notifyError(error)
                })
        );
    }

    protected toggleActive(user: AdminUser): void {
        const activate = !user.active;
        this.confirm.ask(
            {
                header: activate ? 'Ativar conta' : 'Desativar conta',
                message: activate ? `Permitir que ${user.email} volte a entrar?` : `Desativar ${user.email}? As sessões serão encerradas e o login ficará bloqueado. Carteira e operações são mantidas.`,
                acceptLabel: activate ? 'Ativar' : 'Desativar',
                danger: !activate
            },
            () =>
                this.admin.setUserActive(user.id, activate).subscribe({
                    next: () => {
                        this.messages.add({ severity: 'success', closable: false, summary: activate ? 'Conta ativada' : 'Conta desativada', detail: user.email });
                        this.load();
                    },
                    error: (error: unknown) => this.notifyError(error)
                })
        );
    }

    private selfWarning(user: AdminUser): string {
        return user.id === this.auth.user()?.id ? ' Isso inclui a sua sessão atual.' : '';
    }

    private notifyError(error: unknown): void {
        this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível concluir', detail: errorMessage(error) });
    }

    private load(): void {
        this.loading.set(true);
        this.admin.users(this.query, this.page, PAGE_SIZE).subscribe({
            next: ({ items, total }) => {
                this.users.set(items);
                this.total.set(total);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }
}
