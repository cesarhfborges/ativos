import { endOfDay, startOfDay, subDays } from 'date-fns';
import { AssetType, TransactionStatus, TransactionType } from './api.models';

const DEFAULT_PERIOD_DAYS = 90;

export interface StatementFilter {
    /** Parte do símbolo ou do nome. */
    asset: string;
    type: TransactionType | null;
    assetType: AssetType | null;
    status: TransactionStatus | null;
    from: Date | null;
    to: Date | null;
    minTotal: number | null;
    maxTotal: number | null;
}

export const EMPTY_STATEMENT_FILTER: StatementFilter = { asset: '', type: null, assetType: null, status: null, from: null, to: null, minTotal: null, maxTotal: null };

/** Como o extrato abre: os últimos 90 dias. Limpar as duas datas mostra todo o histórico. */
export function defaultStatementFilter(today: Date): StatementFilter {
    return { ...EMPTY_STATEMENT_FILTER, from: subDays(today, DEFAULT_PERIOD_DAYS), to: today };
}

export function hasActiveFilter(filter: StatementFilter): boolean {
    return (Object.keys(EMPTY_STATEMENT_FILTER) as (keyof StatementFilter)[]).some((key) => (key === 'asset' ? filter.asset.trim() !== '' : filter[key] !== null));
}

/** A API recusa um valor mínimo maior que o máximo: a tela avisa, sem fazer a requisição. */
export function isValidFilter(filter: StatementFilter): boolean {
    return filter.minTotal === null || filter.maxTotal === null || filter.minTotal <= filter.maxTotal;
}

/** Só o que foi preenchido. As datas valem o dia inteiro, no fuso de quem está filtrando. */
export function statementParams(filter: StatementFilter): Record<string, string | number> {
    const params: Record<string, string | number> = {};
    const asset = filter.asset.trim();
    if (asset) {
        params['asset'] = asset;
    }
    if (filter.type) {
        params['type'] = filter.type;
    }
    if (filter.assetType) {
        params['assetType'] = filter.assetType;
    }
    if (filter.status) {
        params['status'] = filter.status;
    }
    if (filter.from) {
        params['from'] = startOfDay(filter.from).toISOString();
    }
    if (filter.to) {
        params['to'] = endOfDay(filter.to).toISOString();
    }
    if (filter.minTotal !== null) {
        params['minTotal'] = filter.minTotal;
    }
    if (filter.maxTotal !== null) {
        params['maxTotal'] = filter.maxTotal;
    }
    return params;
}
