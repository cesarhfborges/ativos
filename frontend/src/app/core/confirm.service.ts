import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
    header: string;
    message: string;
    /** Texto do botão de confirmar: deve dizer o que vai acontecer, como "Sair" ou "Remover". */
    acceptLabel: string;
    /** Use ao remover ou apagar algo: destaca o botão como uma ação perigosa. */
    danger?: boolean;
}

export interface ConfirmRequest extends ConfirmOptions {
    onAccept: () => void;
    /** Quem abriu o diálogo, para receber o foco de volta em caso de desistência. */
    opener: HTMLElement | null;
}

/** Toda ação difícil de desfazer passa por aqui, para pedir confirmação sempre do mesmo jeito. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
    private readonly current = signal<ConfirmRequest | null>(null);

    readonly request = this.current.asReadonly();

    ask(options: ConfirmOptions, onAccept: () => void): void {
        const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        this.current.set({ ...options, onAccept, opener });
    }
}
