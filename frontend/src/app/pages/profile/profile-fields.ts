import { Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { InvestorProfile } from '@/app/core/api.models';
import { controlError } from '@/app/core/form-errors';

/** Campos opcionais do perfil, os mesmos no cadastro e na tela de perfil. */
@Component({
    selector: 'app-profile-fields',
    imports: [DatePickerModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectModule],
    template: `<div class="flex flex-col gap-6" [formGroup]="form()">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div class="flex flex-col gap-2">
                <label [attr.for]="idPrefix() + '-phone'" class="font-medium">Telefone <span class="text-muted-color font-normal">(opcional)</span></label>
                <input
                    pInputText
                    [id]="idPrefix() + '-phone'"
                    type="tel"
                    autocomplete="tel"
                    class="w-full"
                    formControlName="phone"
                    [invalid]="!!error('phone')"
                    [attr.aria-invalid]="!!error('phone')"
                    [attr.aria-describedby]="error('phone') ? idPrefix() + '-phone-error' : null"
                />
                @if (error('phone'); as message) {
                    <p-message [id]="idPrefix() + '-phone-error'" severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="flex flex-col gap-2">
                <label [attr.for]="idPrefix() + '-birth'" class="font-medium">Data de nascimento <span class="text-muted-color font-normal">(opcional)</span></label>
                <p-datepicker [inputId]="idPrefix() + '-birth'" formControlName="birthDate" dateFormat="dd/mm/yy" appendTo="body" [showIcon]="true" [fluid]="true" [maxDate]="today" [invalid]="!!error('birthDate')" />
                @if (error('birthDate'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
        </div>
        <div class="flex flex-col gap-2">
            <span [id]="idPrefix() + '-investor-label'" class="font-medium">Perfil de investidor <span class="text-muted-color font-normal">(opcional)</span></span>
            <p-select [ariaLabelledBy]="idPrefix() + '-investor-label'" formControlName="investorProfile" optionLabel="label" optionValue="value" class="w-full" [options]="investorProfiles" />
        </div>
    </div>`
})
export class ProfileFields {
    protected readonly today = new Date();

    readonly form = input.required<FormGroup>();
    /** Evita ids repetidos quando o componente aparece em mais de uma tela. */
    readonly idPrefix = input.required<string>();

    protected readonly investorProfiles: { label: string; value: InvestorProfile | null }[] = [
        { label: 'Não informado', value: null },
        { label: 'Conservador', value: 'CONSERVATIVE' },
        { label: 'Moderado', value: 'MODERATE' },
        { label: 'Arrojado', value: 'AGGRESSIVE' }
    ];

    protected error(name: string): string | null {
        return controlError(this.form().get(name));
    }
}
