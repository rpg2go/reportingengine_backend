import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8101/api/auth';
  isAuthenticated = signal<boolean>(localStorage.getItem('token') !== null);

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<any> {
    const token = btoa(`${username}:${password}`);
    const headers = new HttpHeaders({
      'Authorization': `Basic ${token}`
    });

    return this.http.get(`${this.apiUrl}/login`, { headers }).pipe(
      tap(() => {
        localStorage.setItem('token', token);
        localStorage.setItem('username', username);
        this.isAuthenticated.set(true);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.isAuthenticated.set(false);
  }

  getAuthHeader(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': token ? `Basic ${token}` : ''
    });
  }

  getUsername(): string {
    return localStorage.getItem('username') || '';
  }
}
