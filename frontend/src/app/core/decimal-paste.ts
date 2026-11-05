import { Directive, ElementRef, inject, OnInit } from '@angular/core';

/** Ponto só é milhar em grupos de três dígitos, como `1.234`; em `0.5` ou `1234.56`, é a casa decimal. */
export function normalizePastedNumber(text: string): string {
    const value = text.trim();
    const hasDecimalPoint = /^\d+\.\d+$/.test(value) && !/^\d{1,3}(\.\d{3})+$/.test(value);
    return hasDecimalPoint ? value.replace('.', ',') : value;
}

/**
 * O p-inputnumber em pt-BR descarta o ponto ao colar, e `0.5` vira `5` sem nenhum aviso.
 * Corrige o texto antes que ele chegue ao componente, na fase de captura do evento.
 */
@Directive({ selector: 'p-inputnumber[appDecimalPaste]' })
export class DecimalPaste implements OnInit {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    ngOnInit(): void {
        this.host.nativeElement.addEventListener('paste', (event) => this.onPaste(event), { capture: true });
    }

    private onPaste(event: ClipboardEvent): void {
        const text = event.clipboardData?.getData('text') ?? '';
        const normalized = normalizePastedNumber(text);
        if (normalized === text || !event.target) {
            return;
        }
        event.stopPropagation();
        event.preventDefault();

        const clipboardData = new DataTransfer();
        clipboardData.setData('text/plain', normalized);
        event.target.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }));
    }
}
