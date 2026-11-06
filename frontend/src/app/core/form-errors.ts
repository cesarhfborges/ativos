import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FieldError } from './api-error';

/** O BCrypt só considera 72 bytes, e um caractere acentuado ocupa mais de um. */
export const maxBytes =
    (limit: number): ValidatorFn =>
    (control: AbstractControl): ValidationErrors | null =>
        new TextEncoder().encode(String(control.value ?? '')).length > limit ? { maxBytes: limit } : null;

/** Rejeita zero e negativos; o `required` cuida do campo vazio. */
export const positive: ValidatorFn = (control: AbstractControl): ValidationErrors | null => (control.value !== null && control.value <= 0 ? { positive: true } : null);

/** Mesmo formato aceito pelo backend. Vazio é válido, porque o campo é opcional. */
export const phoneFormat: ValidatorFn = (control: AbstractControl): ValidationErrors | null => (control.value && !/^\+?[0-9 ()-]{8,20}$/.test(String(control.value).trim()) ? { phone: true } : null);

/** Validador de grupo: o erro aparece no formulário, e a tela o mostra no campo de confirmação. */
export const passwordsMatch =
    (passwordField: string, confirmField: string): ValidatorFn =>
    (group: AbstractControl): ValidationErrors | null =>
        group.get(passwordField)?.value === group.get(confirmField)?.value ? null : { passwordMismatch: true };

/** Texto do primeiro erro de um campo, ou `null` se não houver o que mostrar ainda. */
export function controlError(control: AbstractControl | null): string | null {
    if (!control?.errors || !control.touched) {
        return null;
    }
    const errors = control.errors;
    if (errors['server']) return String(errors['server']);
    if (errors['required']) return 'Preencha este campo.';
    if (errors['phone']) return 'Use de 8 a 20 dígitos, como +55 11 91234-5678.';
    if (errors['email']) return 'Informe um e-mail válido.';
    if (errors['minlength']) return `Use pelo menos ${errors['minlength'].requiredLength} caracteres.`;
    if (errors['maxlength']) return `Use no máximo ${errors['maxlength'].requiredLength} caracteres.`;
    if (errors['maxBytes']) return `Use no máximo ${errors['maxBytes']} bytes; acentos contam em dobro.`;
    if (errors['pattern']) return 'Use até 20 letras, números, ponto, hífen ou sublinhado, sem espaços.';
    if (errors['positive']) return 'Informe um valor maior que zero.';
    if (errors['belowFee']) return 'O valor precisa ser maior que a taxa.';
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
