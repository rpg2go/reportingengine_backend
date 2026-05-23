import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class ReportService {
  private apiUrl = 'http://localhost:8101/api/reports';

  constructor(private http: HttpClient, private authService: AuthService) {}

  getReports(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl, {
      headers: this.authService.getAuthHeader()
    });
  }

  getReportConfig(id: string, date: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}?date=${date}`, {
      headers: this.authService.getAuthHeader()
    });
  }

  importTemplate(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post(`${this.apiUrl}/import`, formData, {
      headers: this.authService.getAuthHeader()
    });
  }

  runReport(id: string, date: string): Observable<Blob> {
    return this.http.post(`${this.apiUrl}/${id}/run?date=${date}`, null, {
      headers: this.authService.getAuthHeader(),
      responseType: 'blob'
    });
  }

  getSemanticModel(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/semantic-model`, {
      headers: this.authService.getAuthHeader()
    });
  }
}
