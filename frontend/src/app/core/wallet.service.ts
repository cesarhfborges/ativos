import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BuyRequest, FiatMovementRequest, OperationResponse, SellRequest, Transaction, WalletSummary } from './api.models';

@Injectable({ providedIn: 'root' })
export class WalletService {
    private readonly http = inject(HttpClient);

    summary(): Observable<WalletSummary> {
        return this.http.get<WalletSummary>('/api/wallets/summary');
    }

    fiatMovement(body: FiatMovementRequest): Observable<OperationResponse> {
        return this.http.post<OperationResponse>('/api/wallets/fiat-movement', body);
    }

    buy(body: BuyRequest): Observable<OperationResponse> {
        return this.http.post<OperationResponse>('/api/transactions/buy', body);
    }

    sell(body: SellRequest): Observable<OperationResponse> {
        return this.http.post<OperationResponse>('/api/transactions/sell', body);
    }

    statement(page: number, size: number): Observable<Transaction[]> {
        return this.http.get<Transaction[]>('/api/transactions', { params: { page, size } });
    }
}
