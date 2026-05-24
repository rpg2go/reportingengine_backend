import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../services/report.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="dashboard-container">
      <!-- Sidebar / Header -->
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span class="brand-icon">📊</span>
          <span class="brand-text">Reporting Engine</span>
        </div>

        <nav class="sidebar-menu">
          <a routerLink="/dashboard" class="menu-item active">
            <span class="menu-icon">📁</span>
            <span>Reports</span>
          </a>
          <a routerLink="/semantic" class="menu-item">
            <span class="menu-icon">🧠</span>
            <span>Semantic Layer</span>
          </a>
        </nav>

        <div class="sidebar-user">
          <div class="user-info">
            <span class="user-avatar">👤</span>
            <div class="user-details">
              <span class="user-name">{{ username }}</span>
              <span class="user-role">Administrator</span>
            </div>
          </div>
          <button (click)="logout()" class="logout-btn">Sign Out</button>
        </div>
      </aside>

      <!-- Main Content -->
      <main class="main-content">
        <header class="content-header">
          <div>
            <h1>Report Templates Catalog</h1>
            <p>Import and configure spreadsheet-first report layouts.</p>
          </div>

          <div class="header-actions" style="display: flex; gap: 12px; align-items: center;">
            <button routerLink="/reports/new/edit" class="create-btn">
              <span>➕ Create Report</span>
            </button>
            <div class="file-uploader">
              <label for="template-file" class="upload-label" [class.uploading]="uploading()">
                @if (uploading()) {
                  <span class="spinner"></span> Ingesting...
                } @else {
                  <span>📥 Import Template (.xlsx)</span>
                }
                <input 
                  type="file" 
                  id="template-file" 
                  (change)="onFileSelected($event)" 
                  accept=".xlsx" 
                  [disabled]="uploading()"
                  style="display: none;"
                />
              </label>
            </div>
          </div>
        </header>

        @if (successMessage()) {
          <div class="alert success-alert animate-fade-in">
            <span class="alert-icon">✓</span>
            <span>{{ successMessage() }}</span>
          </div>
        }

        @if (errorMessage()) {
          <div class="alert error-alert animate-fade-in">
            <span class="alert-icon">⚠️</span>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        <!-- Search & Filter Bar -->
        @if (!loading() && reports().length > 0) {
          <div class="search-filter-bar mb-6 animate-fade-in">
            <div class="search-input-wrapper">
              <span class="search-icon">🔍</span>
              <input 
                type="text" 
                [(ngModel)]="searchQuery" 
                placeholder="Search templates by ID, title, details..." 
                class="search-input"
              />
              @if (searchQuery()) {
                <button (click)="searchQuery.set('')" class="clear-search-btn">✕</button>
              }
            </div>
            
            <div class="filter-chips">
              <button 
                class="filter-chip-btn" 
                [class.active]="filterStatus() === 'all'" 
                (click)="filterStatus.set('all')"
              >
                All Templates ({{ getReportsCountByStatus('all') }})
              </button>
              <button 
                class="filter-chip-btn" 
                [class.active]="filterStatus() === 'published'" 
                (click)="filterStatus.set('published')"
              >
                🟢 Published ({{ getReportsCountByStatus('published') }})
              </button>
              <button 
                class="filter-chip-btn" 
                [class.active]="filterStatus() === 'draft'" 
                (click)="filterStatus.set('draft')"
              >
                🟡 Drafts ({{ getReportsCountByStatus('draft') }})
              </button>
            </div>
          </div>
        }

        <!-- Grid of Report Cards -->
        @if (loading()) {
          <div class="loading-state">
            <span class="spinner large"></span>
            <p>Loading report catalog...</p>
          </div>
        } @else if (reports().length === 0) {
          <div class="empty-state">
            <span class="empty-icon">📭</span>
            <h3>No Reports Loaded</h3>
            <p>Upload a <code>dwh_reports_showcase.xlsx</code> template to seed the database catalog.</p>
          </div>
        } @else {
          <div class="reports-grid animate-fade-in">
            @for (report of filteredReports; track report.reportId) {
              <div class="report-card" (click)="viewReport(report.reportId)">
                <div class="card-header">
                  <span class="report-badge" [class.published]="report.status === 'published'">
                    {{ report.status }}
                  </span>
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <button class="edit-icon-btn" (click)="editReport(report.reportId, $event)" title="Edit definition">✏️</button>
                    <span class="report-version">v{{ report.version }}</span>
                  </div>
                </div>
                
                <h3 class="report-title">{{ report.reportId }}</h3>
                <h4 class="report-subtitle">{{ report.name }}</h4>
                <p class="report-desc">
                  {{ report.description || 'No description provided. Click to inspect columns, rows, and execute the layout.' }}
                </p>

                <div class="card-footer">
                  <span class="explore-ref">🎯 Default Explore: {{ report.exploreId || 'Not set' }}</span>
                  <span class="arrow-icon">→</span>
                </div>
              </div>
            }
          </div>

          @if (filteredReports.length === 0) {
            <div class="empty-state animate-fade-in">
              <span class="empty-icon">🔍</span>
              <h3>No Match Found</h3>
              <p>We couldn't find any report templates matching your criteria.</p>
              <button (click)="clearFilters()" class="clear-filters-btn">Reset Filters</button>
            </div>
          }
        }
      </main>
    </div>
  `,
  styles: [`
    .dashboard-container {
      display: flex;
      min-height: 100vh;
      background: #0f172a;
      color: #f8fafc;
      font-family: 'Outfit', 'Inter', sans-serif;
    }

    /* Sidebar Styles */
    .sidebar {
      width: 260px;
      background: rgba(30, 41, 59, 0.5);
      border-right: 1px solid rgba(255, 255, 255, 0.05);
      backdrop-filter: blur(12px);
      display: flex;
      flex-direction: column;
      padding: 24px;
      gap: 32px;
      flex-shrink: 0;
    }

    .sidebar-brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .brand-icon {
      font-size: 28px;
    }

    .brand-text {
      font-size: 20px;
      font-weight: 700;
      background: linear-gradient(135deg, #818cf8 0%, #c084fc 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .sidebar-menu {
      display: flex;
      flex-direction: column;
      gap: 8px;
      flex-grow: 1;
    }

    .menu-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      color: #94a3b8;
      text-decoration: none;
      border-radius: 12px;
      font-weight: 500;
      transition: all 0.2s ease;
    }

    .menu-item:hover, .menu-item.active {
      color: #f8fafc;
      background: rgba(255, 255, 255, 0.05);
    }

    .menu-item.active {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(168, 85, 247, 0.1) 100%);
      border: 1px solid rgba(99, 102, 241, 0.2);
      color: #a5b4fc;
    }

    .menu-icon {
      font-size: 18px;
    }

    .sidebar-user {
      display: flex;
      flex-direction: column;
      gap: 16px;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
      padding-top: 24px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .user-avatar {
      font-size: 24px;
      width: 40px;
      height: 40px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 50%;
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .user-details {
      display: flex;
      flex-direction: column;
    }

    .user-name {
      font-size: 14px;
      font-weight: 600;
    }

    .user-role {
      font-size: 12px;
      color: #64748b;
    }

    .logout-btn {
      width: 100%;
      padding: 10px;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.2);
      border-radius: 8px;
      color: #fca5a5;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .logout-btn:hover {
      background: rgba(239, 68, 68, 0.2);
      color: white;
    }

    /* Main Content Styles */
    .main-content {
      flex-grow: 1;
      padding: 40px;
      overflow-y: auto;
    }

    .content-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 40px;
      gap: 20px;
    }

    h1 {
      font-size: 32px;
      font-weight: 700;
      margin: 0 0 8px 0;
      letter-spacing: -0.5px;
    }

    .content-header p {
      color: #94a3b8;
      font-size: 15px;
      margin: 0;
    }

    .create-btn {
      padding: 14px 24px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      color: white;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 10px;
      transition: all 0.2s ease;
    }

    .create-btn:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(99, 102, 241, 0.4);
      transform: translateY(-1px);
    }

    .edit-icon-btn {
      background: none;
      border: none;
      color: #94a3b8;
      font-size: 14px;
      cursor: pointer;
      padding: 4px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
      z-index: 5;
    }

    .edit-icon-btn:hover {
      color: #818cf8;
      background: rgba(255, 255, 255, 0.08);
    }

    .upload-label {
      padding: 14px 24px;
      background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
      border-radius: 12px;
      color: white;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 10px;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
      transition: all 0.2s ease;
    }

    .upload-label:hover {
      background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
      box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
      transform: translateY(-1px);
    }

    .upload-label.uploading {
      opacity: 0.8;
      cursor: not-allowed;
    }

    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    .spinner.large {
      width: 40px;
      height: 40px;
      border-width: 3px;
      border-top-color: #6366f1;
    }

    /* Alerts */
    .alert {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 20px;
      border-radius: 12px;
      margin-bottom: 24px;
      font-size: 14px;
      font-weight: 500;
    }

    .success-alert {
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid rgba(16, 185, 129, 0.3);
      color: #a7f3d0;
    }

    .error-alert {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #fca5a5;
    }

    .alert-icon {
      font-size: 18px;
      font-weight: bold;
    }

    /* Loading / Empty States */
    .loading-state, .empty-state {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      padding: 80px 40px;
      text-align: center;
      background: rgba(30, 41, 59, 0.2);
      border: 1px dashed rgba(255, 255, 255, 0.1);
      border-radius: 24px;
      gap: 16px;
    }

    .empty-icon {
      font-size: 64px;
    }

    .empty-state h3 {
      font-size: 20px;
      margin: 0;
    }

    .empty-state p {
      color: #64748b;
      margin: 0;
      max-width: 400px;
    }

    /* Reports Grid */
    .reports-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 24px;
    }

    .report-card {
      background: rgba(30, 41, 59, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 20px;
      padding: 24px;
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      flex-direction: column;
      position: relative;
      overflow: hidden;
    }

    .report-card::after {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(168, 85, 247, 0.02) 100%);
      opacity: 0;
      transition: opacity 0.3s ease;
      z-index: 0;
      pointer-events: none;
    }

    .report-card:hover {
      border-color: rgba(99, 102, 241, 0.2);
      transform: translateY(-2px);
      box-shadow: 0 12px 20px rgba(0, 0, 0, 0.2);
    }

    .report-card:hover::after {
      opacity: 1;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      z-index: 1;
    }

    .report-badge {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 4px 10px;
      border-radius: 20px;
      background: rgba(245, 158, 11, 0.15);
      color: #fde047;
      border: 1px solid rgba(245, 158, 11, 0.2);
    }

    .report-badge.published {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.2);
    }

    .report-version {
      font-size: 12px;
      color: #64748b;
      font-weight: 500;
    }

    .report-title {
      font-size: 20px;
      font-weight: 700;
      margin: 0 0 4px 0;
      z-index: 1;
    }

    .report-subtitle {
      font-size: 14px;
      font-weight: 600;
      color: #818cf8;
      margin: 0 0 16px 0;
      z-index: 1;
    }

    .report-desc {
      font-size: 13px;
      color: #94a3b8;
      line-height: 1.6;
      margin: 0 0 24px 0;
      flex-grow: 1;
      z-index: 1;
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
      padding-top: 16px;
      z-index: 1;
    }

    .explore-ref {
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
    }

    .arrow-icon {
      font-size: 16px;
      color: #64748b;
      transition: transform 0.2s ease;
    }

    .report-card:hover .arrow-icon {
      color: #818cf8;
      transform: translateX(4px);
    }

    .report-card:hover .arrow-icon {
      color: #818cf8;
      transform: translateX(4px);
    }

    /* PREMIUM SEARCH & QUICK FILTERS */
    .mb-6 {
      margin-bottom: 24px;
    }

    .search-filter-bar {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
      padding: 18px 24px;
      background: rgba(30, 41, 59, 0.3);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 16px;
      backdrop-filter: blur(8px);
    }

    .search-input-wrapper {
      position: relative;
      width: 480px;
      max-width: 100%;
    }

    .search-icon {
      position: absolute;
      left: 14px;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
      font-size: 16px;
      pointer-events: none;
    }

    .search-input {
      width: 100%;
      padding: 10px 40px 10px 42px;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      color: #f8fafc;
      font-size: 14px;
      outline: none;
      transition: all 0.2s ease;
      font-family: inherit;
    }

    .search-input:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15);
      background: rgba(15, 23, 42, 0.8);
    }

    .clear-search-btn {
      position: absolute;
      right: 12px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: #64748b;
      cursor: pointer;
      font-size: 12px;
      padding: 4px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }

    .clear-search-btn:hover {
      color: #f8fafc;
      background: rgba(255, 255, 255, 0.1);
    }

    .filter-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }

    .filter-chip-btn {
      padding: 8px 16px;
      background: rgba(15, 23, 42, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 20px;
      font-size: 13px;
      font-weight: 500;
      color: #cbd5e1;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .filter-chip-btn:hover {
      background: rgba(255, 255, 255, 0.05);
      color: #f8fafc;
      border-color: rgba(255, 255, 255, 0.15);
    }

    .filter-chip-btn.active {
      background: rgba(99, 102, 241, 0.15);
      color: #a5b4fc;
      border-color: rgba(99, 102, 241, 0.4);
      box-shadow: 0 0 12px rgba(99, 102, 241, 0.1);
    }

    .clear-filters-btn {
      margin-top: 8px;
      padding: 8px 18px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 8px;
      color: #a5b4fc;
      font-weight: 600;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .clear-filters-btn:hover {
      background: rgba(99, 102, 241, 0.25);
      color: white;
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
export class DashboardComponent implements OnInit {
  reports = signal<any[]>([]);
  loading = signal(true);
  uploading = signal(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  username = '';

  // Search & Filter signals
  searchQuery = signal('');
  filterStatus = signal('all'); // 'all', 'draft', 'published'

  constructor(private reportService: ReportService, private authService: AuthService, private router: Router) {
    this.username = this.authService.getUsername();
  }

  ngOnInit(): void {
    this.loadCatalog();
  }

  loadCatalog(): void {
    this.loading.set(true);
    this.reportService.getReports().subscribe({
      next: (data) => {
        this.reports.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load report templates catalog.');
      }
    });
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (!file) return;

    this.uploading.set(true);
    this.successMessage.set(null);
    this.errorMessage.set(null);

    this.reportService.importTemplate(file).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.successMessage.set('Template Excel configurations imported successfully!');
        this.loadCatalog();
      },
      error: (err) => {
        this.uploading.set(false);
        this.errorMessage.set(err.error?.message || 'Error occurred during template ingestion.');
      }
    });
  }

  viewReport(reportId: string): void {
    this.router.navigate(['/reports', reportId]);
  }

  editReport(reportId: string, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/reports', reportId, 'edit']);
  }

  // Filter calculations & helper actions
  get filteredReports(): any[] {
    const query = this.searchQuery().toLowerCase().trim();
    const status = this.filterStatus();
    
    return this.reports().filter((report: any) => {
      // Status match
      if (status !== 'all' && report.status !== status) {
        return false;
      }
      
      // Query match
      return !query || 
        report.reportId.toLowerCase().includes(query) || 
        report.name.toLowerCase().includes(query) || 
        (report.description && report.description.toLowerCase().includes(query)) ||
        (report.exploreId && String(report.exploreId).toLowerCase().includes(query)) ||
        (report.sourceTable && report.sourceTable.toLowerCase().includes(query));
    });
  }

  getReportsCountByStatus(status: string): number {
    if (status === 'all') return this.reports().length;
    return this.reports().filter(r => r.status === status).length;
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.filterStatus.set('all');
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
