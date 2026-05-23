import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  template: `
    <div class="login-container">
      <div class="glass-card animate-fade-in">
        <div class="header-logo">
          <div class="logo-circle">
            <span class="logo-icon">📊</span>
          </div>
          <h1>Headless BI</h1>
          <p>Reporting Engine Platform</p>
        </div>

        <form (ngSubmit)="onSubmit()" #loginForm="ngForm" class="login-form">
          <div class="form-group">
            <label for="username">Username</label>
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input 
                type="text" 
                id="username" 
                name="username" 
                [(ngModel)]="username" 
                required 
                placeholder="Enter admin"
                #usernameInput="ngModel"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <div class="input-wrapper">
              <span class="input-icon">🔒</span>
              <input 
                type="password" 
                id="password" 
                name="password" 
                [(ngModel)]="password" 
                required 
                placeholder="Enter password"
                #passwordInput="ngModel"
              />
            </div>
          </div>

          @if (errorMessage()) {
            <div class="error-banner">
              <span class="error-icon">⚠️</span>
              <span class="error-text">{{ errorMessage() }}</span>
            </div>
          }

          <button type="submit" [disabled]="loading() || !loginForm.valid" class="submit-btn">
            @if (loading()) {
              <span class="spinner"></span> Authenticating...
            } @else {
              Sign In
            }
          </button>
        </form>

        <div class="login-footer">
          <p>Default credentials: <code>admin</code> / <code>password</code></p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: radial-gradient(circle at 10% 20%, rgb(15, 23, 42) 0%, rgb(30, 41, 59) 90%);
      font-family: 'Outfit', 'Inter', sans-serif;
      color: #f1f5f9;
      padding: 20px;
    }

    .glass-card {
      background: rgba(30, 41, 59, 0.7);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 24px;
      padding: 40px;
      width: 100%;
      max-width: 440px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .glass-card:hover {
      border-color: rgba(99, 102, 241, 0.3);
      box-shadow: 0 20px 40px rgba(99, 102, 241, 0.15);
    }

    .header-logo {
      text-align: center;
      margin-bottom: 32px;
    }

    .logo-circle {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      width: 64px;
      height: 64px;
      background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
      border-radius: 50%;
      margin-bottom: 16px;
      box-shadow: 0 8px 16px rgba(99, 102, 241, 0.4);
    }

    .logo-icon {
      font-size: 32px;
    }

    h1 {
      font-size: 28px;
      font-weight: 700;
      margin: 0 0 6px 0;
      letter-spacing: -0.5px;
      background: linear-gradient(to right, #f8fafc, #cbd5e1);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    p {
      color: #94a3b8;
      font-size: 14px;
      margin: 0;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    label {
      font-size: 13px;
      font-weight: 600;
      color: #cbd5e1;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .input-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }

    .input-icon {
      position: absolute;
      left: 14px;
      font-size: 18px;
      color: #64748b;
    }

    input {
      width: 100%;
      padding: 14px 14px 14px 44px;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      color: #f8fafc;
      font-size: 15px;
      outline: none;
      transition: all 0.2s ease;
    }

    input:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
      background: rgba(15, 23, 42, 0.8);
    }

    .error-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: 10px;
      padding: 12px;
    }

    .error-icon {
      font-size: 18px;
      color: #ef4444;
    }

    .error-text {
      font-size: 13px;
      color: #fca5a5;
    }

    .submit-btn {
      padding: 14px;
      background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
      border: none;
      border-radius: 12px;
      color: white;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 10px;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
      transition: all 0.2s ease;
    }

    .submit-btn:hover:not(:disabled) {
      background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
      box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
      transform: translateY(-1px);
    }

    .submit-btn:active:not(:disabled) {
      transform: translateY(0);
    }

    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      box-shadow: none;
    }

    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    .login-footer {
      text-align: center;
      margin-top: 24px;
      font-size: 12px;
      color: #64748b;
    }

    code {
      background: rgba(15, 23, 42, 0.8);
      padding: 2px 6px;
      border-radius: 4px;
      color: #a855f7;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .animate-fade-in {
      animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  loading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(private authService: AuthService, private router: Router) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onSubmit(): void {
    if (!this.username || !this.password) return;

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Invalid username or password. Please try again.');
      }
    });
  }
}
