import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';
import { Transaction } from './api.models';
import { errorMessage } from './api-error';
import { ConfirmService } from './confirm.service';
import { TRANSACTION_DISPLAY } from './transaction-display';

@Injectable({ providedIn: 'root' })
export class TransactionReversalService {
    private readonly http = inject(HttpClient);
    private readonly confirm = inject(ConfirmService);
    private readonly messages = inject(MessageService);

    /** Pede confirmação, estorna e avisa o resultado; `onReversed` só roda se o estorno acontecer. */
    reverse(transaction: Transaction, onReversed: () => void): void {
        const operation = `${TRANSACTION_DISPLAY[transaction.type].label.toLowerCase()} de ${transaction.symbol}`;
        this.confirm.ask(
            {
                header: 'Estornar operação',
                message: `Estornar a ${operation}? Ela continua no extrato como estornada, e a posição, o preço médio e o lucro das vendas seguintes serão recalculados. Esta ação não pode ser desfeita.`,
                acceptLabel: 'Estornar',
                danger: true
            },
            () =>
                this.http.post<Transaction>(`/api/transactions/${transaction.id}/reverse`, null).subscribe({
                    next: () => {
                        this.messages.add({ severity: 'success', closable: false, summary: 'Operação estornada', detail: `A ${operation} não conta mais nos cálculos.` });
                        onReversed();
                    },
                    error: (error: unknown) => this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível estornar', detail: errorMessage(error) })
                })
        );
    }
}
