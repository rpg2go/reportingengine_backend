import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../services/report.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-semantic',
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
              <!-- Search & Quick Filters for Explores -->
              <div class="search-filter-bar mb-6 animate-fade-in">
                <div class="search-input-wrapper">
                  <span class="search-icon">🔍</span>
                  <input 
                    type="text" 
                    [(ngModel)]="exploreSearchQuery" 
                    placeholder="Search explores by name or fact view..." 
                    class="search-input"
                  />
                  @if (exploreSearchQuery()) {
                    <button (click)="exploreSearchQuery.set('')" class="clear-search-btn">✕</button>
                  }
                </div>
                
                <div class="filter-chips">
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="exploreFilterType() === 'all'" 
                    (click)="exploreFilterType.set('all')"
                  >
                    All Explores ({{ modelData().explores.length }})
                  </button>
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="exploreFilterType() === 'joins'" 
                    (click)="exploreFilterType.set('joins')"
                  >
                    🔗 With Joins ({{ getExploresWithJoinsCount() }})
                  </button>
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="exploreFilterType() === 'direct'" 
                    (click)="exploreFilterType.set('direct')"
                  >
                    🎯 Direct Fact Only ({{ getExploresDirectCount() }})
                  </button>
                </div>
              </div>

              <div class="explores-list">
                @for (explore of filteredExplores; track explore.explore_id) {
                  <div class="glass-card mb-6 card-hover">
                    <div class="card-title-bar">
                      <div class="flex-column">
                        <span class="badge explore-badge">Explore</span>
                        <h3>{{ explore.name }}</h3>
                      </div>
                      <div class="text-right">
                        <span class="subtext">Fact View:</span>
                        <code class="code-highlight fact-view-code">{{ explore.fact_view_name }}</code>
                      </div>
                    </div>
                    
                    @if (explore.sql_always_where) {
                      <div class="sql-box mt-3 mb-3">
                        <span class="sql-label">Always Where Filter:</span>
                        <pre><code>{{ explore.sql_always_where }}</code></pre>
                      </div>
                    }

                    <div class="joins-section mt-4">
                      <div class="joins-header flex-header">
                        <h4 class="section-title">🔗 Join Relationships</h4>
                        <span class="joins-count-badge">
                          {{ getJoinsForExplore(explore.name).length }} {{ getJoinsForExplore(explore.name).length === 1 ? 'Join' : 'Joins' }}
                        </span>
                      </div>
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
                                  <td><code class="code-highlight dim-view-code">{{ join.to_view }}</code></td>
                                  <td class="sql-cell"><code>{{ join.join_sql }}</code></td>
                                </tr>
                              }
                            </tbody>
                          </table>
                        </div>
                      }
                    </div>
                  </div>
                }
                
                @if (filteredExplores.length === 0) {
                  <div class="empty-state animate-fade-in">
                    <span class="empty-icon">🔍</span>
                    <h4>No Explores Found</h4>
                    <p>We couldn't find any explores matching your search terms or filters.</p>
                    <button (click)="clearExploreFilters()" class="clear-filters-btn">Reset Filters</button>
                  </div>
                }
              </div>
            }

            <!-- Views Tab -->
            @if (activeTab() === 'views') {
              <!-- Search & Quick Filters for Views -->
              <div class="search-filter-bar mb-6 animate-fade-in">
                <div class="search-input-wrapper">
                  <span class="search-icon">🔍</span>
                  <input 
                    type="text" 
                    [(ngModel)]="viewSearchQuery" 
                    placeholder="Search views by name, table, description..." 
                    class="search-input"
                  />
                  @if (viewSearchQuery()) {
                    <button (click)="viewSearchQuery.set('')" class="clear-search-btn">✕</button>
                  }
                </div>
                
                <div class="filter-chips">
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="viewFilterType() === 'all'" 
                    (click)="viewFilterType.set('all')"
                  >
                    All Views ({{ modelData().views.length }})
                  </button>
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="viewFilterType() === 'fact'" 
                    (click)="viewFilterType.set('fact')"
                  >
                    📊 Fact Views ({{ getViewsCountByType('fact') }})
                  </button>
                  <button 
                    class="filter-chip-btn" 
                    [class.active]="viewFilterType() === 'dimension'" 
                    (click)="viewFilterType.set('dimension')"
                  >
                    📐 Dimension Views ({{ getViewsCountByType('dimension') }})
                  </button>
                </div>
              </div>

              <div class="views-list">
                @for (view of filteredViews; track view.view_id) {
                  <div class="glass-card mb-6 card-hover">
                    <div class="card-title-bar">
                      <div class="flex-column">
                        <span class="badge" [class.badge-fact]="view.view_type === 'fact'">
                          {{ view.view_type }}
                        </span>
                        <h3>{{ view.name }}</h3>
                      </div>
                      <div class="text-right">
                        <span class="subtext">Physical Table:</span>
                        <code class="code-highlight table-code">{{ view.table_ref }}</code>
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
                      <span class="stat-pill">📐 {{ getDimensionsForView(view.name).length }} Dimensions</span>
                      <span class="stat-pill measure">📊 {{ getMeasuresForView(view.name).length }} Measures</span>
                    </div>

                    <!-- Bento-like columns for Dimensions and Measures -->
                    <div class="inner-tabs">
                      <div class="inner-grid">
                        <!-- Dimensions Column -->
                        <div class="column-section dimensions-column">
                          <h4 class="section-title text-dim">📐 Dimensions</h4>
                          @if (getDimensionsForView(view.name).length === 0) {
                            <p class="no-data-msg">No dimensions defined for this view.</p>
                          } @else {
                            <div class="list-wrapper">
                              @for (dim of getDimensionsForView(view.name); track dim.dimension_id) {
                                <div class="list-item dim-item">
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
                        <div class="column-section measures-column">
                          <h4 class="section-title text-measure">📊 Measures</h4>
                          @if (getMeasuresForView(view.name).length === 0) {
                            <p class="no-data-msg">No measures defined for this view.</p>
                          } @else {
                            <div class="list-wrapper">
                              @for (meas of getMeasuresForView(view.name); track meas.measure_id) {
                                <div class="list-item meas-item">
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
                
                @if (filteredViews.length === 0) {
                  <div class="empty-state animate-fade-in">
                    <span class="empty-icon">🔍</span>
                    <h4>No Views Found</h4>
                    <p>We couldn't find any views matching your search terms or filters.</p>
                    <button (click)="clearViewFilters()" class="clear-filters-btn">Reset Filters</button>
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
      flex-wrap: wrap;
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

    /* PREMIUM UX ADDITIONS */
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

    /* Cards Hover and Glow */
    .card-hover {
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .card-hover:hover {
      transform: translateY(-3px);
      border-color: rgba(99, 102, 241, 0.25);
      box-shadow: 0 12px 30px rgba(15, 23, 42, 0.6), 0 0 20px rgba(99, 102, 241, 0.08);
      background: rgba(30, 41, 59, 0.55);
    }

    /* Stat Pills */
    .stat-pill {
      font-size: 12px;
      background: rgba(14, 165, 233, 0.1);
      padding: 6px 12px;
      border-radius: 10px;
      border: 1px solid rgba(14, 165, 233, 0.2);
      color: #38bdf8;
      font-weight: 500;
    }

    .stat-pill.measure {
      background: rgba(34, 197, 94, 0.1);
      border-color: rgba(34, 197, 94, 0.2);
      color: #4ade80;
    }

    /* Empty state */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 40px;
      text-align: center;
      background: rgba(30, 41, 59, 0.25);
      border: 1px dashed rgba(255, 255, 255, 0.08);
      border-radius: 20px;
      gap: 12px;
      width: 100%;
    }

    .empty-icon {
      font-size: 32px;
      color: #64748b;
    }

    .empty-state h4 {
      font-size: 18px;
      font-weight: 600;
      margin: 0;
      color: #f8fafc;
    }

    .empty-state p {
      font-size: 14px;
      color: #94a3b8;
      margin: 0 0 8px 0;
      max-width: 320px;
    }

    .clear-filters-btn {
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

    /* Section count badge */
    .joins-count-badge {
      font-size: 11px;
      font-weight: 600;
      background: rgba(255, 255, 255, 0.05);
      padding: 4px 8px;
      border-radius: 6px;
      color: #cbd5e1;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }

    @media (max-width: 1024px) {
      .inner-grid {
        grid-template-columns: 1fr;
      }
    }

    .column-section.dimensions-column {
      border-left: 2px solid rgba(14, 165, 233, 0.3);
    }

    .column-section.measures-column {
      border-left: 2px solid rgba(34, 197, 94, 0.3);
    }

    .text-dim {
      color: #38bdf8 !important;
    }

    .text-measure {
      color: #4ade80 !important;
    }

    .dim-item {
      border-left: 2px solid rgba(14, 165, 233, 0.5) !important;
    }

    .meas-item {
      border-left: 2px solid rgba(34, 197, 94, 0.5) !important;
    }

    .explore-badge {
      background: rgba(99, 102, 241, 0.15) !important;
      color: #c7d2fe !important;
      border-color: rgba(99, 102, 241, 0.25) !important;
    }

    .fact-view-code {
      color: #f472b6 !important;
    }

    .dim-view-code {
      color: #38bdf8 !important;
    }

    .table-code {
      color: #a7f3d0 !important;
    }
  `]
})
export class SemanticViewerComponent implements OnInit {
  modelData = signal<any>({ views: [], explores: [], joins: [], dimensions: [], measures: [] });
  loading = signal(true);
  activeTab = signal<string>('explores');
  username = '';

  // Search & Filter state
  exploreSearchQuery = signal('');
  exploreFilterType = signal('all'); // 'all', 'joins', 'direct'

  viewSearchQuery = signal('');
  viewFilterType = signal('all'); // 'all', 'fact', 'dimension'

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

  // Filter calculations & getters
  get filteredExplores(): any[] {
    const query = this.exploreSearchQuery().toLowerCase().trim();
    const filter = this.exploreFilterType();
    
    return this.modelData().explores.filter((explore: any) => {
      const matchesQuery = !query || 
        explore.name.toLowerCase().includes(query) || 
        (explore.fact_view_name && explore.fact_view_name.toLowerCase().includes(query));
      
      if (!matchesQuery) return false;
      
      const joinCount = this.getJoinsForExplore(explore.name).length;
      if (filter === 'joins') {
        return joinCount > 0;
      } else if (filter === 'direct') {
        return joinCount === 0;
      }
      return true;
    });
  }

  get filteredViews(): any[] {
    const query = this.viewSearchQuery().toLowerCase().trim();
    const filter = this.viewFilterType();
    
    return this.modelData().views.filter((view: any) => {
      const matchesQuery = !query || 
        view.name.toLowerCase().includes(query) || 
        (view.table_ref && view.table_ref.toLowerCase().includes(query)) ||
        (view.description && view.description.toLowerCase().includes(query));
        
      if (!matchesQuery) return false;
      
      if (filter === 'fact') {
        return view.view_type === 'fact';
      } else if (filter === 'dimension') {
        return view.view_type === 'dimension';
      }
      return true;
    });
  }

  getExploresWithJoinsCount(): number {
    return this.modelData().explores.filter((e: any) => this.getJoinsForExplore(e.name).length > 0).length;
  }

  getExploresDirectCount(): number {
    return this.modelData().explores.filter((e: any) => this.getJoinsForExplore(e.name).length === 0).length;
  }

  getViewsCountByType(type: string): number {
    return this.modelData().views.filter((v: any) => v.view_type === type).length;
  }

  clearExploreFilters(): void {
    this.exploreSearchQuery.set('');
    this.exploreFilterType.set('all');
  }

  clearViewFilters(): void {
    this.viewSearchQuery.set('');
    this.viewFilterType.set('all');
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
