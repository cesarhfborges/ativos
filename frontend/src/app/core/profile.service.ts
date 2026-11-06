import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangePasswordRequest, Profile, ProfileDetails } from './api.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private readonly http = inject(HttpClient);

    get(): Observable<Profile> {
        return this.http.get<Profile>('/api/profile');
    }

    update(body: ProfileDetails & { name: string }): Observable<Profile> {
        return this.http.put<Profile>('/api/profile', body);
    }

    changePassword(body: ChangePasswordRequest): Observable<void> {
        return this.http.post<void>('/api/profile/password', body);
    }
}
