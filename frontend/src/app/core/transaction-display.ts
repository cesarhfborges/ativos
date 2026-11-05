import { TransactionType } from './api.models';

/** Compra é a entrada do ativo na carteira; venda, a saída. */
export const TRANSACTION_DISPLAY: Record<TransactionType, { label: string; icon: string }> = {
    BUY: { label: 'Compra', icon: 'pi pi-arrow-down-left' },
    SELL: { label: 'Venda', icon: 'pi pi-arrow-up-right' }
};
