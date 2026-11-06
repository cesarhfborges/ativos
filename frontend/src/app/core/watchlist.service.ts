import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AddWatchlistItem, WatchlistItem, WatchlistPlan } from './api.models';

const API = '/api/watchlist';

@Injectable({ providedIn: 'root' })
export class WatchlistService {
    private readonly http = inject(HttpClient);

    list(days: number): Observable<WatchlistItem[]> {
        return this.http.get<WatchlistItem[]>(API, { params: { days } });
    }

    add(body: AddWatchlistItem): Observable<WatchlistItem> {
        return this.http.post<WatchlistItem>(API, body);
    }

    update(id: string, plan: WatchlistPlan): Observable<WatchlistItem> {
        return this.http.put<WatchlistItem>(`${API}/${id}`, plan);
    }

    remove(id: string): Observable<void> {
        return this.http.delete<void>(`${API}/${id}`);
    }
}
