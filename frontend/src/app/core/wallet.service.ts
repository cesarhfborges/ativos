import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Asset, BuyRequest, DashboardOverview, DashboardPosition, PortfolioHistoryPoint, SellRequest, Transaction, TransactionType } from './api.models';

@Injectable({ providedIn: 'root' })
export class WalletService {
    private readonly http = inject(HttpClient);

    searchAssets(query: string): Observable<Asset[]> {
        return this.http.get<Asset[]>('/api/assets', { params: { query } });
    }

    overview(): Observable<DashboardOverview> {
        return this.http.get<DashboardOverview>('/api/dashboard/overview');
    }

    positions(): Observable<DashboardPosition[]> {
        return this.http.get<DashboardPosition[]>('/api/dashboard/positions');
    }

    history(days: number): Observable<PortfolioHistoryPoint[]> {
        return this.http.get<PortfolioHistoryPoint[]>('/api/dashboard/history', { params: { days } });
    }

    /** `refreshed` é falso quando já houve uma coleta no último minuto. */
    refreshQuotes(): Observable<{ refreshed: boolean }> {
        return this.http.post<{ refreshed: boolean }>('/api/dashboard/refresh-quotes', null);
    }

    buy(body: BuyRequest): Observable<Transaction> {
        return this.http.post<Transaction>('/api/transactions/buy', body);
    }

    sell(body: SellRequest): Observable<Transaction> {
        return this.http.post<Transaction>('/api/transactions/sell', body);
    }

    statement(page: number, size: number, type?: TransactionType | null): Observable<Transaction[]> {
        return this.http.get<Transaction[]>('/api/transactions', { params: { page, size, ...(type ? { type } : {}) } });
    }
}
