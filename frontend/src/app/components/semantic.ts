import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ReportService } from '../services/report.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-semantic',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="dashboard-container">
      <!-- Sidebar / Header -->
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span class="brand-icon">📊</span>
          <span class="brand-text">Headless BI</span>
        </div>

        <nav class="sidebar-menu">
          <a routerLink="/dashboard" class="menu-item">
            <span class="menu-icon">📁</span>
            <span>Reports</span>
          </a>
          <a routerLink="/semantic" class="menu-item active">
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
            <h1>Semantic Data Catalog</h1>
            <p>Logical views, dimensions, measures, and join paths driving dynamic report generation.</p>
          </div>
        </header>

        @if (loading()) {
          <div class="loading-state">
            <span class="spinner large"></span>
            <p>Loading semantic definitions...</p>
          </div>
        } @else {
          <!-- Tabs -->
          <div class="tabs-header">
            <button 
              class="tab-btn" 
              [class.active]="activeTab() === 'explores'" 
              (click)="activeTab.set('explores')"
            >
              🎯 Explores & Joins
            </button>
            <button 
              class="tab-btn" 
              [class.active]="activeTab() === 'views'" 
              (click)="activeTab.set('views')"
            >
              👁️ Views & Schema Mapping
            </button>
          </div>

          <div class="tab-content animate-fade-in">
            <!-- Explores Tab -->
            @if (activeTab() === 'explores') {
              <div class="explores-list">
                @for (explore of modelData().explores; track explore.explore_id) {
                  <div class="glass-card mb-6">
                    <div class="card-title-bar">
                      <div class="flex-column">
                        <span class="badge">Explore</span>
                        <h3>{{ explore.name }}</h3>
                      </div>
                      <div class="text-right">
                        <span class="subtext">Fact View:</span>
                        <code class="code-highlight">{{ explore.fact_view_name }}</code>
                      </div>
                    </div>
                    
                    @if (explore.sql_always_where) {
                      <div class="sql-box mt-3 mb-3">
                        <span class="sql-label">Always Where Filter:</span>
                        <pre><code>{{ explore.sql_always_where }}</code></pre>
                      </div>
                    }

                    <h4 class="section-title mt-4">🔗 Join Relationships</h4>
                    @if (getJoinsForExplore(explore.name).length === 0) {
                      <p class="no-data-msg">No dimensions joined to this explore. It queries the fact view directly.</p>
                    } @else {
                      <div class="table-wrapper mt-2">
                        <table class="grid-table">
                          <thead>
                            <tr>
                              <th>Type</th>
                              <th>Dimension View</th>
                              <th>Join Condition (ON)</th>
                            </tr>
                          </thead>
                          <tbody>
                            @for (join of getJoinsForExplore(explore.name); track join.join_id) {
                              <tr>
                                <td class="bold-text"><span class="join-type-badge">{{ join.join_type }}</span></td>
                                <td><code class="code-highlight">{{ join.to_view }}</code></td>
                                <td class="sql-cell"><code>{{ join.join_sql }}</code></td>
                              </tr>
                            }
                          </tbody>
                        </table>
                      </div>
                    }
                  </div>
                }
              </div>
            }

            <!-- Views Tab -->
            @if (activeTab() === 'views') {
              <div class="views-list">
                @for (view of modelData().views; track view.view_id) {
                  <div class="glass-card mb-6">
                    <div class="card-title-bar">
                      <div class="flex-column">
                        <span class="badge" [class.badge-fact]="view.view_type === 'fact'">
                          {{ view.view_type }}
                        </span>
                        <h3>{{ view.name }}</h3>
                      </div>
                      <div class="text-right">
                        <span class="subtext">Physical Table:</span>
                        <code class="code-highlight">{{ view.table_ref }}</code>
                      </div>
                    </div>

                    <p class="description-text">{{ view.description || 'No description available for this view.' }}</p>

                    <div class="view-keys mt-2 mb-4">
                      @if (view.primary_key) {
                        <span class="key-pill">🔑 PK: <code>{{ view.primary_key }}</code></span>
                      }
                      @if (view.time_key) {
                        <span class="key-pill">📅 Time Key: <code>{{ view.time_key }}</code></span>
                      }
                    </div>

                    <!-- Tabs inside View: Dimensions & Measures -->
                    <div class="inner-tabs">
                      <div class="inner-grid">
                        <!-- Dimensions Column -->
                        <div class="column-section">
                          <h4 class="section-title">📐 Dimensions</h4>
                          @if (getDimensionsForView(view.name).length === 0) {
                            <p class="no-data-msg">No dimensions defined for this view.</p>
                          } @else {
                            <div class="list-wrapper">
                              @for (dim of getDimensionsForView(view.name); track dim.dimension_id) {
                                <div class="list-item">
                                  <div class="item-header">
                                    <span class="item-name">{{ dim.name }}</span>
                                    <span class="item-type-badge">{{ dim.data_type }}</span>
                                  </div>
                                  <div class="item-body">
                                    <code class="code-small">Ref: {{ dim.column_ref }}</code>
                                    @if (dim.description) {
                                      <p class="item-desc">{{ dim.description }}</p>
                                    }
                                  </div>
                                </div>
                              }
                            </div>
                          }
                        </div>

                        <!-- Measures Column -->
                        <div class="column-section">
                          <h4 class="section-title">📊 Measures</h4>
                          @if (getMeasuresForView(view.name).length === 0) {
                            <p class="no-data-msg">No measures defined for this view.</p>
                          } @else {
                            <div class="list-wrapper">
                              @for (meas of getMeasuresForView(view.name); track meas.measure_id) {
                                <div class="list-item">
                                  <div class="item-header">
                                    <span class="item-name">{{ meas.name }}</span>
                                    <span class="item-type-badge measure">{{ meas.data_type || 'numeric' }}</span>
                                  </div>
                                  <div class="item-body">
                                    <div class="sql-code">
                                      <span class="agg-badge">{{ meas.agg_type }}</span>
                                      <code>{{ meas.sql_expr }}</code>
                                    </div>
                                    @if (meas.description) {
                                      <p class="item-desc mt-1">{{ meas.description }}</p>
                                    }
                                  </div>
                                </div>
                              }
                            </div>
                          }
                        </div>
                      </div>
                    </div>
                  </div>
                }
              </div>
            }
          </div>
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
      margin-bottom: 32px;
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

    /* Tabs Header */
    .tabs-header {
      display: flex;
      gap: 12px;
      margin-bottom: 24px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      padding-bottom: 12px;
    }

    .tab-btn {
      background: transparent;
      border: none;
      color: #94a3b8;
      font-size: 16px;
      font-weight: 600;
      padding: 8px 16px;
      cursor: pointer;
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    .tab-btn:hover {
      color: #f8fafc;
      background: rgba(255, 255, 255, 0.03);
    }

    .tab-btn.active {
      color: #818cf8;
      background: rgba(99, 102, 241, 0.1);
    }

    /* Glass Cards */
    .glass-card {
      background: rgba(30, 41, 59, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 20px;
      padding: 24px;
      position: relative;
    }

    .mb-6 { margin-bottom: 24px; }
    .mt-2 { margin-top: 8px; }
    .mt-3 { margin-top: 12px; }
    .mt-4 { margin-top: 16px; }
    .mb-3 { margin-bottom: 12px; }
    .mb-4 { margin-bottom: 16px; }

    .card-title-bar {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 12px;
    }

    .flex-column {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .badge {
      align-self: flex-start;
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      padding: 3px 8px;
      border-radius: 12px;
      background: rgba(168, 85, 247, 0.15);
      color: #d8b4fe;
      border: 1px solid rgba(168, 85, 247, 0.25);
    }

    .badge-fact {
      background: rgba(236, 72, 153, 0.15);
      color: #fbcfe8;
      border-color: rgba(236, 72, 153, 0.25);
    }

    .card-title-bar h3 {
      font-size: 22px;
      font-weight: 700;
      margin: 0;
      color: #f1f5f9;
    }

    .text-right {
      text-align: right;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .subtext {
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
    }

    .code-highlight {
      font-size: 13px;
      padding: 4px 8px;
      border-radius: 6px;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.05);
      color: #38bdf8;
      font-family: monospace;
    }

    .description-text {
      font-size: 14px;
      color: #94a3b8;
      line-height: 1.6;
      margin: 0 0 16px 0;
    }

    /* Keys section */
    .view-keys {
      display: flex;
      gap: 12px;
    }

    .key-pill {
      font-size: 12px;
      background: rgba(15, 23, 42, 0.4);
      padding: 6px 12px;
      border-radius: 10px;
      border: 1px solid rgba(255, 255, 255, 0.05);
      color: #cbd5e1;
    }

    .key-pill code {
      color: #fb7185;
      font-family: monospace;
    }

    /* SQL box styles */
    .sql-box {
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 10px;
      padding: 12px 16px;
    }

    .sql-label {
      font-size: 11px;
      color: #64748b;
      font-weight: 600;
      text-transform: uppercase;
      display: block;
      margin-bottom: 6px;
    }

    .sql-box pre {
      margin: 0;
    }

    .sql-box code {
      font-family: monospace;
      color: #e2e8f0;
      font-size: 13px;
    }

    /* Section Title */
    .section-title {
      font-size: 14px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #818cf8;
      margin-bottom: 12px;
    }

    /* Tables */
    .table-wrapper {
      background: rgba(15, 23, 42, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 12px;
      overflow: hidden;
    }

    .grid-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }

    .grid-table th {
      padding: 12px 16px;
      background: rgba(15, 23, 42, 0.8);
      font-weight: 600;
      color: #cbd5e1;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    }

    .grid-table td {
      padding: 12px 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      color: #94a3b8;
    }

    .grid-table tr:last-child td {
      border-bottom: none;
    }

    .bold-text {
      font-weight: 600;
      color: #f1f5f9;
    }

    .join-type-badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(99, 102, 241, 0.2);
      color: #c7d2fe;
    }

    .sql-cell code {
      font-family: monospace;
      color: #f472b6;
    }

    .no-data-msg {
      font-size: 13px;
      color: #64748b;
      font-style: italic;
      margin: 8px 0;
    }

    /* Inner Tabs Grid */
    .inner-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }

    .column-section {
      background: rgba(15, 23, 42, 0.2);
      border-radius: 12px;
      padding: 16px;
      border: 1px solid rgba(255, 255, 255, 0.02);
    }

    /* List styling */
    .list-wrapper {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .list-item {
      background: rgba(15, 23, 42, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.04);
      border-radius: 10px;
      padding: 12px;
      transition: all 0.2s ease;
    }

    .list-item:hover {
      border-color: rgba(255, 255, 255, 0.1);
      background: rgba(15, 23, 42, 0.6);
    }

    .item-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }

    .item-name {
      font-size: 14px;
      font-weight: 600;
      color: #e2e8f0;
    }

    .item-type-badge {
      font-size: 10px;
      font-family: monospace;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(56, 189, 248, 0.15);
      color: #7dd3fc;
    }

    .item-type-badge.measure {
      background: rgba(34, 197, 94, 0.15);
      color: #86efac;
    }

    .code-small {
      font-size: 11px;
      font-family: monospace;
      color: #64748b;
      display: block;
      margin-bottom: 4px;
    }

    .item-desc {
      font-size: 12px;
      color: #94a3b8;
      margin: 4px 0 0 0;
      line-height: 1.4;
    }

    .sql-code {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
    }

    .agg-badge {
      font-size: 9px;
      font-weight: 700;
      padding: 2px 4px;
      border-radius: 4px;
      background: rgba(249, 115, 22, 0.2);
      color: #ffedd5;
    }

    .sql-code code {
      font-family: monospace;
      color: #fbbf24;
    }

    /* Loading state */
    .loading-state {
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
export class SemanticViewerComponent implements OnInit {
  modelData = signal<any>({ views: [], explores: [], joins: [], dimensions: [], measures: [] });
  loading = signal(true);
  activeTab = signal<string>('explores');
  username = '';

  constructor(
    private reportService: ReportService,
    private authService: AuthService,
    private router: Router
  ) {
    this.username = this.authService.getUsername();
  }

  ngOnInit(): void {
    this.loadSemanticModel();
  }

  loadSemanticModel(): void {
    this.loading.set(true);
    this.reportService.getSemanticModel().subscribe({
      next: (data) => {
        this.modelData.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        console.error('Failed to load semantic model metadata', err);
      }
    });
  }

  getJoinsForExplore(exploreName: string): any[] {
    return this.modelData().joins.filter((j: any) => j.explore_name === exploreName);
  }

  getDimensionsForView(viewName: string): any[] {
    return this.modelData().dimensions.filter((d: any) => d.view_name === viewName);
  }

  getMeasuresForView(viewName: string): any[] {
    return this.modelData().measures.filter((m: any) => m.view_name === viewName);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
