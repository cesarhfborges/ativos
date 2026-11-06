import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Asset, AssetLookup, AssetType, BuyRequest, DashboardOverview, DashboardPosition, MarketAsset, PortfolioHistoryPoint, PortfolioSentiment, SellRequest, SimplePage, Transaction } from './api.models';
import { EMPTY_STATEMENT_FILTER, StatementFilter, statementParams } from './statement-filter';

@Injectable({ providedIn: 'root' })
export class WalletService {
    private readonly http = inject(HttpClient);

    searchAssets(query: string): Observable<Asset[]> {
        return this.http.get<Asset[]>('/api/assets', { params: { query } });
    }

    /** Procura no provedor de cotações, inclusive ativos que ainda não estão no catálogo. */
    lookupAssets(query: string, type: AssetType): Observable<AssetLookup> {
        return this.http.get<AssetLookup>('/api/assets/lookup', { params: { query, type } });
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

    market(days: number): Observable<MarketAsset[]> {
        return this.http.get<MarketAsset[]>('/api/dashboard/market', { params: { days } });
    }

    sentiment(days: number): Observable<PortfolioSentiment> {
        return this.http.get<PortfolioSentiment>('/api/dashboard/sentiment', { params: { days } });
    }

    buy(body: BuyRequest): Observable<Transaction> {
        return this.http.post<Transaction>('/api/transactions/buy', body);
    }

    sell(body: SellRequest): Observable<Transaction> {
        return this.http.post<Transaction>('/api/transactions/sell', body);
    }

    statement(filter: StatementFilter, page: number, perPage: number): Observable<SimplePage<Transaction>> {
        return this.http.get<SimplePage<Transaction>>('/api/transactions', { params: { ...statementParams(filter), page, per_page: perPage } });
    }

    recentTransactions(count: number): Observable<Transaction[]> {
        return this.statement(EMPTY_STATEMENT_FILTER, 1, count).pipe(map((page) => page.data));
    }
}
