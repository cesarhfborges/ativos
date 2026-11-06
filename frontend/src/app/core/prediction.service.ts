import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SimulationRequest, SimulationResponse } from './api.models';

@Injectable({ providedIn: 'root' })
export class PredictionService {
    private readonly http = inject(HttpClient);

    simulate(body: SimulationRequest): Observable<SimulationResponse> {
        return this.http.post<SimulationResponse>('/api/predictions/simulate', body);
    }
}
