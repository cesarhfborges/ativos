import { HttpErrorResponse } from '@angular/common/http';
import { AssetType } from './api.models';

export interface FieldError {
    field: string;
    message: string;
}

interface ProblemBody {
    detail?: unknown;
    errors?: unknown;
}

function isFieldError(value: unknown): value is FieldError {
    const candidate = value as Partial<FieldError> | null;
    return typeof candidate?.field === 'string' && typeof candidate?.message === 'string';
}

/** Mensagem para o usuário: o `detail` do problem+json quando o erro é da regra de negócio. */
export function errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
        if (error.status === 0) {
            return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
        }
        const detail = (error.error as ProblemBody | null)?.detail;
        if (error.status < 500 && typeof detail === 'string') {
            return detail;
        }
        // Sem detalhe, o 403 vem do filtro de segurança: token CSRF vencido ou falta de permissão.
        if (error.status === 403) {
            return 'A operação foi recusada. Recarregue a página e tente novamente.';
        }
    }
    return 'Ocorreu um erro inesperado. Tente novamente em instantes.';
}

/** O provedor não encontrou o símbolo; `suggestedType` é o tipo em que ele existe, se existir. */
export interface SymbolIssue {
    detail: string;
    suggestedType: AssetType | null;
}

export function symbolIssue(error: unknown): SymbolIssue | null {
    if (!(error instanceof HttpErrorResponse)) {
        return null;
    }
    const body = error.error as (ProblemBody & { code?: unknown; suggestedType?: AssetType | null }) | null;
    return body?.code === 'SYMBOL_NOT_FOUND' && typeof body.detail === 'string' ? { detail: body.detail, suggestedType: body.suggestedType ?? null } : null;
}

/** Erros por campo devolvidos no `400` de validação do backend. */
export function fieldErrors(error: unknown): FieldError[] {
    if (!(error instanceof HttpErrorResponse)) {
        return [];
    }
    const errors = (error.error as ProblemBody | null)?.errors;
    return Array.isArray(errors) ? errors.filter(isFieldError) : [];
}
