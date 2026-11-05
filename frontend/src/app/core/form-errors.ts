import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FieldError } from './api-error';

/** O BCrypt só considera 72 bytes, e um caractere acentuado ocupa mais de um. */
export const maxBytes =
    (limit: number): ValidatorFn =>
    (control: AbstractControl): ValidationErrors | null =>
        new TextEncoder().encode(String(control.value ?? '')).length > limit ? { maxBytes: limit } : null;

/** Rejeita zero e negativos; o `required` cuida do campo vazio. */
export const positive: ValidatorFn = (control: AbstractControl): ValidationErrors | null => (control.value !== null && control.value <= 0 ? { positive: true } : null);

/** Texto do primeiro erro de um campo, ou `null` se não houver o que mostrar ainda. */
export function controlError(control: AbstractControl | null): string | null {
    if (!control?.errors || !control.touched) {
        return null;
    }
    const errors = control.errors;
    if (errors['server']) return String(errors['server']);
    if (errors['required']) return 'Preencha este campo.';
    if (errors['email']) return 'Informe um e-mail válido.';
    if (errors['minlength']) return `Use pelo menos ${errors['minlength'].requiredLength} caracteres.`;
    if (errors['maxlength']) return `Use no máximo ${errors['maxlength'].requiredLength} caracteres.`;
    if (errors['maxBytes']) return `Use no máximo ${errors['maxBytes']} bytes; acentos contam em dobro.`;
    if (errors['pattern']) return 'Use até 20 letras, números, ponto, hífen ou sublinhado, sem espaços.';
    if (errors['positive']) return 'Informe um valor maior que zero.';
    if (errors['max']) return `O máximo disponível é ${errors['max'].max.toLocaleString('pt-BR', { maximumFractionDigits: 8 })}.`;
    return 'Valor inválido.';
}

/** Mostra nos campos os erros de validação devolvidos pelo backend. */
export function applyFieldErrors(form: FormGroup, errors: FieldError[]): boolean {
    let applied = false;
    for (const { field, message } of errors) {
        const control = form.get(field);
        if (control) {
            control.setErrors({ server: message });
            control.markAsTouched();
            applied = true;
        }
    }
    return applied;
}
