import { DestroyRef, Directive, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePicker } from 'primeng/datepicker';

/**
 * Nos botões de hora e minuto, o PrimeNG 21 coloca o rótulo no p-button, e não no botão de verdade,
 * que fica sem nome para leitores de tela. Esta diretiva repassa o rótulo quando o calendário abre.
 */
@Directive({ selector: 'p-datepicker[appDatePickerLabels]' })
export class DatePickerLabels implements OnInit {
    private readonly datePicker = inject(DatePicker);
    private readonly destroyRef = inject(DestroyRef);

    ngOnInit(): void {
        this.datePicker.onShow.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            document.querySelectorAll('.p-datepicker-panel p-button[aria-label]').forEach((host) => {
                host.querySelector('button')?.setAttribute('aria-label', host.getAttribute('aria-label') ?? '');
            });
        });
    }
}
