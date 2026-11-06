import { formatDate } from '@angular/common';

/** Instante que a API espera; sem data, ela considera a operação como de agora. */
export function toInstant(value: Date | null | undefined): string | undefined {
    return value ? value.toISOString() : undefined;
}

/** Trecho da mensagem de confirmação; vazio quando a operação é de agora. */
export function dateClause(value: Date | null | undefined): string {
    return value ? `, em ${formatDate(value, 'short', 'pt-BR')}` : '';
}

/** Data sem horário, no fuso local, como `yyyy-MM-dd`: `toISOString` poderia cair no dia anterior ou seguinte. */
export function toIsoDate(value: Date | null | undefined): string | null {
    return value ? formatDate(value, 'yyyy-MM-dd', 'pt-BR') : null;
}

export function fromIsoDate(value: string | null | undefined): Date | null {
    return value ? new Date(`${value}T00:00:00`) : null;
}
