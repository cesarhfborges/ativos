import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdminOverview, AdminSettings, AdminUser, AdminUserCreate, AdminUserUpdate, AssetType, AssetVerification, AuditEntry, CatalogAsset, Page, SyncItem, SyncRun, SyncRunFilter } from './api.models';

const API = '/api/admin';

@Injectable({ providedIn: 'root' })
export class AdminService {
    private readonly http = inject(HttpClient);

    overview(): Observable<AdminOverview> {
        return this.http.get<AdminOverview>(`${API}/overview`);
    }

    /** `refreshed` é falso quando a última coleta foi há menos que o intervalo configurado. */
    collectQuotes(): Observable<{ refreshed: boolean }> {
        return this.http.post<{ refreshed: boolean }>(`${API}/quotes/collect`, null);
    }

    syncRuns(filter: SyncRunFilter, page: number, size: number): Observable<Page<SyncRun>> {
        return this.http.get<Page<SyncRun>>(`${API}/sync/runs`, {
            params: {
                page,
                size,
                ...(filter.status ? { status: filter.status } : {}),
                ...(filter.origin ? { origin: filter.origin } : {}),
                ...(filter.symbol.trim() ? { symbol: filter.symbol.trim() } : {})
            }
        });
    }

    syncRunItems(id: string): Observable<SyncItem[]> {
        return this.http.get<SyncItem[]>(`${API}/sync/runs/${id}/items`);
    }

    rebuildPositions(): Observable<{ wallets: number; positions: number }> {
        return this.http.post<{ wallets: number; positions: number }>(`${API}/positions/rebuild`, null);
    }

    users(query: string, page: number, size: number): Observable<Page<AdminUser>> {
        return this.http.get<Page<AdminUser>>(`${API}/users`, { params: { query, page, size } });
    }

    createUser(body: AdminUserCreate): Observable<AdminUser> {
        return this.http.post<AdminUser>(`${API}/users`, body);
    }

    updateUser(id: string, body: AdminUserUpdate): Observable<AdminUser> {
        return this.http.put<AdminUser>(`${API}/users/${id}`, body);
    }

    setUserActive(id: string, active: boolean): Observable<AdminUser> {
        return this.http.put<AdminUser>(`${API}/users/${id}/active`, { active });
    }

    resetPassword(id: string, password: string): Observable<void> {
        return this.http.post<void>(`${API}/users/${id}/password`, { password });
    }

    endSessions(id: string): Observable<void> {
        return this.http.post<void>(`${API}/users/${id}/sessions/end`, null);
    }

    assets(query: string, page: number, size: number): Observable<Page<CatalogAsset>> {
        return this.http.get<Page<CatalogAsset>>(`${API}/assets`, { params: { query, page, size } });
    }

    updateAsset(id: string, name: string, assetType: AssetType): Observable<CatalogAsset> {
        return this.http.put<CatalogAsset>(`${API}/assets/${id}`, { name, assetType });
    }

    verifyAsset(id: string): Observable<AssetVerification> {
        return this.http.post<AssetVerification>(`${API}/assets/${id}/verify`, null);
    }

    removeAsset(id: string): Observable<void> {
        return this.http.delete<void>(`${API}/assets/${id}`);
    }

    settings(): Observable<AdminSettings> {
        return this.http.get<AdminSettings>(`${API}/settings`);
    }

    updateSettings(values: Record<string, string>): Observable<AdminSettings> {
        return this.http.put<AdminSettings>(`${API}/settings`, { values });
    }

    restoreSetting(key: string): Observable<AdminSettings> {
        return this.http.delete<AdminSettings>(`${API}/settings/${key}`);
    }

    audit(page: number, size: number): Observable<Page<AuditEntry>> {
        return this.http.get<Page<AuditEntry>>(`${API}/audit`, { params: { page, size } });
    }
}
