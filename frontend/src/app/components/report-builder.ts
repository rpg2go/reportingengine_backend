import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../services/report.service';
import { AuthService } from '../services/auth.service';
import { forkJoin } from 'rxjs';

interface FilterCondition {
  attribute: string;
  operator: string;
  value: string;
}

@Component({
  selector: 'app-report-builder',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="builder-container">
      <!-- Sidebar -->
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span class="brand-icon">🛠️</span>
          <span class="brand-text">Report Builder</span>
        </div>

        <nav class="sidebar-menu">
          <a routerLink="/dashboard" class="menu-item">
            <span class="menu-icon">📁</span>
            <span>Reports Catalog</span>
          </a>
          <a routerLink="/semantic" class="menu-item">
            <span class="menu-icon">🧠</span>
            <span>Semantic Layer</span>
          </a>
        </nav>

        <div class="sidebar-user">
          <button (click)="goBack()" class="back-btn">← Cancel & Exit</button>
        </div>
      </aside>

      <!-- Main Content -->
      <main class="main-content animate-fade-in">
        <!-- Top Sticky Action Bar -->
        <header class="detail-header">
          <div>
            <div class="breadcrumbs">
              <a routerLink="/dashboard">Reports</a> / <span>Builder</span>
            </div>
            <h1>{{ isNewReport ? 'Create New Report' : 'Update Existing Report' }}</h1>
            <p class="report-subtitle">Define structural configurations, formulas, and filter details.</p>
          </div>

          <div class="action-buttons">
            <button (click)="togglePreview()" class="preview-btn">
              👁️ {{ showPreview() ? 'Hide Preview' : 'Preview Layout' }}
            </button>
            <button (click)="saveConfig()" [disabled]="saving()" class="save-btn">
              @if (saving()) {
                <span class="spinner"></span> Saving...
              } @else {
                <span>💾 Save Definition</span>
              }
            </button>
          </div>
        </header>

        <!-- Status Alerts -->
        @if (successMessage()) {
          <div class="alert success-alert">
            <span class="alert-icon">✓</span>
            <span>{{ successMessage() }}</span>
          </div>
        }
        @if (errorMessage()) {
          <div class="alert error-alert">
            <span class="alert-icon">⚠️</span>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        <!-- Preview Modal -->
        @if (showPreview()) {
          <section class="preview-section card animate-fade-in">
            <h3 class="section-title">📊 Live Layout Preview</h3>
            <p class="section-desc">Molded view of rows and active columns. Formula evaluations run during Phase 2.</p>
            <div class="table-wrapper">
              <table class="spreadsheet-table">
                <thead>
                  <tr>
                    <th class="sticky-col">Label</th>
                    <th>ID</th>
                    <th>Type</th>
                    @for (col of columns; track col.colId) {
                      <th class="col-flag-header">
                        <div><code>{{ col.colId }}</code></div>
                        <div class="preview-col-label">{{ col.label }}</div>
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows; track row.rowId) {
                    <tr [class]="'row-style-' + (row.style || 'normal').toLowerCase()">
                      <td class="sticky-col label-cell" [style.padding-left.px]="20 + row.indentLevel * 16">
                        @if (row.rowType === 'section') {
                          📂 <strong>{{ row.label }}</strong>
                        } @else if (row.rowType === 'calc') {
                          🧮 {{ row.label }}
                        } @else if (row.rowType === 'data') {
                          📊 {{ row.label }}
                        } @else {
                          &nbsp;
                        }
                      </td>
                      <td><code>{{ row.rowId }}</code></td>
                      <td><span class="row-type-badge" [class]="row.rowType">{{ row.rowType }}</span></td>
                      @for (col of columns; track col.colId) {
                        <td class="col-flag-cell">
                          @if (row.activeCols && row.activeCols.includes(col.colId.toUpperCase())) {
                            <span class="flag-dot">✓</span>
                          } @else {
                            <span class="flag-dash">-</span>
                          }
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }

        <!-- Header Configuration Panel -->
        <section class="config-panel card">
          <h3 class="section-title">⚙️ Core Report Details</h3>
          <div class="form-grid">
            <div class="form-group">
              <label for="report-id">Report ID* (unique)</label>
              <input 
                type="text" 
                id="report-id" 
                [(ngModel)]="reportId" 
                [disabled]="!isNewReport" 
                placeholder="e.g. RPT_001" 
                class="form-input"
              />
            </div>

            <div class="form-group">
              <label for="report-name">Report Title*</label>
              <input 
                type="text" 
                id="report-name" 
                [(ngModel)]="reportName" 
                placeholder="e.g. Sales Weekly Report" 
                class="form-input"
              />
            </div>

            <div class="form-group">
              <label for="report-version">Version</label>
              <input 
                type="number" 
                id="report-version" 
                [(ngModel)]="reportVersion" 
                class="form-input"
              />
            </div>

            <div class="form-group">
              <label for="report-status">Report Status</label>
              <select id="report-status" [(ngModel)]="status" class="form-select">
                <option value="draft">Draft</option>
                <option value="published">Published</option>
              </select>
            </div>

            <div class="form-group">
              <label for="source-table">Source Table (Fact)*</label>
              <select id="source-table" [(ngModel)]="sourceTable" (change)="onTableChange()" class="form-select">
                <option value="">-- Select database table --</option>
                @for (tbl of dbTables; track tbl) {
                  <option [value]="tbl">{{ tbl }}</option>
                }
              </select>
            </div>

            <div class="form-group">
              <label for="granularity">Report Granularity*</label>
              <select id="granularity" [(ngModel)]="granularity" class="form-select">
                <option value="">-- Select grouping column --</option>
                @for (col of tableColumns; track col) {
                  <option [value]="col">{{ col }}</option>
                }
              </select>
            </div>

            <div class="form-group">
              <label>Timeframe Limit</label>
              <div class="timeframe-inputs">
                <input 
                  type="date" 
                  [(ngModel)]="timeframeStart" 
                  class="form-input" 
                />
                @if (timeframeToday) {
                  <input 
                    type="text" 
                    value="today" 
                    disabled 
                    class="form-input" 
                  />
                } @else {
                  <input 
                    type="date" 
                    [(ngModel)]="timeframeEnd" 
                    class="form-input" 
                  />
                }
                <label class="checkbox-label">
                  <input type="checkbox" [(ngModel)]="timeframeToday" (change)="onTimeframeTodayToggle()" /> Today
                </label>
              </div>
            </div>
          </div>

          <!-- Quick Filters Selection -->
          <div class="form-group filter-select-group">
            <label>Quick Filters (Select columns to expose as quick filters)</label>
            <div class="chip-container">
              @for (col of tableColumns; track col) {
                <span 
                  class="filter-chip" 
                  [class.active]="isQuickFilter(col)" 
                  (click)="toggleQuickFilter(col)"
                >
                  {{ col }}
                </span>
              }
            </div>
          </div>

          <!-- General Filters Builder -->
          <div class="form-group filters-builder">
            <div class="flex-header">
              <label>General Filters (Base Scope Constraints)</label>
              <button (click)="addGeneralFilter()" class="add-sub-btn">+ Add Filter Condition</button>
            </div>
            @if (generalFilters.length === 0) {
              <p class="empty-filters">No general filters configured. Applies to entire database table scope.</p>
            } @else {
              <div class="filters-list">
                @for (filter of generalFilters; track $index; let idx = $index) {
                  <div class="filter-row animate-fade-in">
                    <select [(ngModel)]="filter.attribute" (change)="loadFilterValues(filter)" class="form-select sm">
                      <option value="">-- Select attribute --</option>
                      @for (col of tableColumns; track col) {
                        <option [value]="col">{{ col }}</option>
                      }
                    </select>

                    <select [(ngModel)]="filter.operator" class="form-select sm operator">
                      <option value="is">is</option>
                      <option value="is not">is not</option>
                      <option value="in">in</option>
                      <option value="like">contains</option>
                      <option value="=">=</option>
                      <option value="!=">!=</option>
                      <option value=">">&gt;</option>
                      <option value=">=">&gt;=</option>
                      <option value="<">&lt;</option>
                      <option value="<=">&lt;=</option>
                    </select>

                    <input 
                      type="text" 
                      [(ngModel)]="filter.value" 
                      placeholder="Enter value (e.g. DEU)"
                      list="filter-val-list"
                      class="form-input sm"
                    />
                    <datalist id="filter-val-list">
                      @for (val of getDistinctOptions(filter.attribute); track val) {
                        <option [value]="val">{{ val }}</option>
                      }
                    </datalist>

                    <button (click)="removeGeneralFilter(idx)" class="remove-btn" title="Remove condition">✕</button>
                  </div>
                }
              </div>
            }
          </div>
        </section>

        <!-- Rows Definition Section -->
        <section class="rows-section card">
          <div class="flex-header">
            <div>
              <h3 class="section-title">Rows Setup (Step 1)</h3>
              <p class="section-desc">Define structural rows, labels, formulas, indentations, and row-level attributes.</p>
            </div>
            <div class="table-actions">
              <button (click)="addRow()" class="action-btn-sm add">+ Add Row</button>
              <button (click)="deleteSelectedRows()" class="action-btn-sm delete-selected">🗑️ Delete Selected</button>
              <button (click)="resetRows()" class="action-btn-sm reset">↻ Reset</button>
              <button (click)="duplicateSelectedRow()" class="action-btn-sm duplicate">📄 Duplicate</button>
              <button (click)="reorderRows()" class="action-btn-sm reorder">➔ Reorder</button>
            </div>
          </div>

          <div class="table-wrapper">
            <table class="grid-table">
              <thead>
                <tr>
                  <th style="width: 40px;"><input type="checkbox" (change)="toggleAllRowsSelect($event)" /></th>
                  <th style="width: 70px;">Row ID</th>
                  <th>Row Name (Label)*</th>
                  <th style="width: 160px;">Row Style / Layout</th>
                  <th style="width: 220px;">SQL Expr / Calculation Formula</th>
                  <th style="width: 200px;">Filters (e.g. Lifecycle = 2)</th>
                  <th style="width: 140px;">Active Columns</th>
                  <th style="width: 60px; text-align: center;">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track row.rowId; let idx = $index) {
                  <tr [class.selected]="row.selected">
                    <td><input type="checkbox" [(ngModel)]="row.selected" /></td>
                    <td>
                      <input type="text" [(ngModel)]="row.rowId" placeholder="R1" class="cell-input center" />
                    </td>
                    <td>
                      <div class="indent-wrapper" [style.padding-left.px]="row.indentLevel * 12">
                        <button (click)="changeIndent(row, -1)" class="indent-btn" title="Decrease indent">«</button>
                        <button (click)="changeIndent(row, 1)" class="indent-btn" title="Increase indent">»</button>
                        <input type="text" [(ngModel)]="row.label" placeholder="Row Label" class="cell-input" />
                      </div>
                    </td>
                    <td>
                      <div class="style-cell">
                        <select [(ngModel)]="row.rowType" class="cell-select">
                          <option value="data">📊 data</option>
                          <option value="calc">🧮 calc</option>
                          <option value="section">📂 section</option>
                          <option value="blank">🫙 blank</option>
                        </select>
                        <select [(ngModel)]="row.style" class="cell-select">
                          <option value="normal">Normal</option>
                          <option value="header">Header</option>
                          <option value="section">Section</option>
                          <option value="total">Total</option>
                          <option value="highlight">Highlight</option>
                          <option value="blank">Blank</option>
                        </select>
                      </div>
                    </td>
                    <td>
                      <input 
                        type="text" 
                        [(ngModel)]="row.source" 
                        [placeholder]="row.rowType === 'data' ? 'e.g. SUM(amount)' : row.rowType === 'calc' ? 'e.g. R2 / R3' : '-'" 
                        [disabled]="row.rowType === 'section' || row.rowType === 'blank'"
                        class="cell-input code"
                      />
                    </td>
                    <td>
                      <input 
                        type="text" 
                        [(ngModel)]="row.filterExpr" 
                        [placeholder]="row.rowType === 'data' ? 'e.g. lifecycle = 2' : '-'" 
                        [disabled]="row.rowType !== 'data'"
                        class="cell-input"
                      />
                    </td>
                    <td>
                      <div class="col-enable-toggles">
                        @for (col of columns; track col.colId) {
                          <span 
                            class="col-badge" 
                            [class.active]="row.activeCols.includes(col.colId.toUpperCase())"
                            (click)="toggleColForRow(row, col.colId)"
                          >
                            {{ col.colId }}
                          </span>
                        }
                      </div>
                    </td>
                    <td style="text-align: center;">
                      <button (click)="deleteRow(idx)" class="remove-btn" title="Delete Row">🗑️</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <!-- Columns Definition Section -->
        <section class="columns-section card">
          <div class="flex-header">
            <div>
              <h3 class="section-title">Columns Setup (Step 2)</h3>
              <p class="section-desc">Define column headers, level layouts, timeframe types, rolling offsets, and formulas.</p>
            </div>
            <div class="table-actions">
              <button (click)="addColumn()" class="action-btn-sm add">+ Add Col</button>
              <button (click)="deleteSelectedCols()" class="action-btn-sm delete-selected">🗑️ Delete Selected</button>
              <button (click)="resetColumns()" class="action-btn-sm reset">↻ Reset</button>
              <button (click)="duplicateSelectedColumn()" class="action-btn-sm duplicate">📄 Duplicate</button>
              <button (click)="reorderColumns()" class="action-btn-sm reorder">➔ Reorder</button>
            </div>
          </div>

          <div class="table-wrapper">
            <table class="grid-table">
              <thead>
                <tr>
                  <th style="width: 40px;"><input type="checkbox" (change)="toggleAllColsSelect($event)" /></th>
                  <th style="width: 70px;">Col ID</th>
                  <th>Column Name / Header Label*</th>
                  <th style="width: 140px;">Col Type</th>
                  <th style="width: 160px;">Header Style</th>
                  <th style="width: 100px;">Period Offset</th>
                  <th style="width: 100px;">Rolling N</th>
                  <th style="width: 200px;">Formula / Expression</th>
                  <th style="width: 60px; text-align: center;">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (col of columns; track col.colId; let idx = $index) {
                  <tr [class.selected]="col.selected">
                    <td><input type="checkbox" [(ngModel)]="col.selected" /></td>
                    <td>
                      <input type="text" [(ngModel)]="col.colId" placeholder="C1" class="cell-input center" />
                    </td>
                    <td>
                      <input type="text" [(ngModel)]="col.label" placeholder="Column Header Label" class="cell-input" />
                    </td>
                    <td>
                      <select [(ngModel)]="col.colType" class="cell-select">
                        <option value="WEEK">WEEK</option>
                        <option value="MTD">MTD</option>
                        <option value="YTD">YTD</option>
                        <option value="ROLLING">ROLLING</option>
                        <option value="CALC">CALC</option>
                      </select>
                    </td>
                    <td>
                      <select [(ngModel)]="col.headerLayout" class="cell-select">
                        <option value="normal">Normal</option>
                        <option value="bold">Bold, Center</option>
                        <option value="border">Bold, Border</option>
                      </select>
                    </td>
                    <td>
                      <input 
                        type="number" 
                        [(ngModel)]="col.periodOffset" 
                        [disabled]="col.colType === 'CALC'" 
                        class="cell-input center" 
                      />
                    </td>
                    <td>
                      <input 
                        type="number" 
                        [(ngModel)]="col.rollingN" 
                        [disabled]="col.colType !== 'ROLLING'" 
                        placeholder="e.g. 10" 
                        class="cell-input center" 
                      />
                    </td>
                    <td>
                      <input 
                        type="text" 
                        [(ngModel)]="col.formulaExpr" 
                        [placeholder]="col.colType === 'CALC' ? 'e.g. (C1-C2)/C2' : '-'" 
                        [disabled]="col.colType !== 'CALC'" 
                        class="cell-input code" 
                      />
                    </td>
                    <td style="text-align: center;">
                      <button (click)="deleteColumn(idx)" class="remove-btn" title="Delete Column">🗑️</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  `,
  styles: [`
    .builder-container {
      display: flex;
      min-height: 100vh;
      background: #0f172a;
      color: #f8fafc;
      font-family: 'Outfit', 'Inter', sans-serif;
    }

    /* Sidebar */
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

    .menu-item:hover {
      color: #f8fafc;
      background: rgba(255, 255, 255, 0.05);
    }

    .back-btn {
      width: 100%;
      padding: 12px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      color: #f8fafc;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .back-btn:hover {
      background: rgba(239, 68, 68, 0.15);
      border-color: rgba(239, 68, 68, 0.3);
      color: #fca5a5;
    }

    /* Main Content */
    .main-content {
      flex-grow: 1;
      padding: 40px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 32px;
      max-width: calc(100vw - 260px);
    }

    .detail-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 20px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      padding-bottom: 24px;
    }

    .breadcrumbs {
      font-size: 13px;
      color: #64748b;
      margin-bottom: 8px;
    }

    .breadcrumbs a {
      color: #818cf8;
      text-decoration: none;
    }

    .breadcrumbs a:hover {
      text-decoration: underline;
    }

    h1 {
      font-size: 32px;
      font-weight: 800;
      margin: 0;
      letter-spacing: -1px;
    }

    .report-subtitle {
      font-size: 15px;
      color: #94a3b8;
      margin: 4px 0 0 0;
    }

    /* Top Action Buttons */
    .action-buttons {
      display: flex;
      gap: 12px;
    }

    .preview-btn {
      padding: 12px 24px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      color: white;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .preview-btn:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: #818cf8;
    }

    .save-btn {
      padding: 12px 24px;
      background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
      border: none;
      border-radius: 10px;
      color: white;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 10px;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
      transition: all 0.2s ease;
    }

    .save-btn:hover:not(:disabled) {
      background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
      box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
    }

    .save-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* Cards */
    .card {
      background: rgba(30, 41, 59, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 20px;
      padding: 32px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .section-title {
      font-size: 20px;
      font-weight: 700;
      margin: 0;
      color: #f8fafc;
    }

    .section-desc {
      font-size: 14px;
      color: #94a3b8;
      margin: 4px 0 0 0;
    }

    /* Core details grid */
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 24px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .form-group label {
      font-size: 13px;
      font-weight: 600;
      color: #94a3b8;
    }

    .form-input, .form-select {
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      padding: 10px 14px;
      color: white;
      outline: none;
      font-size: 14px;
      font-family: inherit;
      transition: all 0.2s ease;
    }

    .form-input:focus, .form-select:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
    }

    .timeframe-inputs {
      display: flex;
      gap: 12px;
      align-items: center;
    }

    .checkbox-label {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: white;
      white-space: nowrap;
      cursor: pointer;
    }

    /* Chip Container */
    .chip-container {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      background: rgba(15, 23, 42, 0.3);
      padding: 12px;
      border-radius: 10px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    .filter-chip {
      padding: 6px 14px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;
      color: #cbd5e1;
    }

    .filter-chip:hover {
      background: rgba(255, 255, 255, 0.1);
      color: white;
    }

    .filter-chip.active {
      background: rgba(99, 102, 241, 0.15);
      color: #a5b4fc;
      border-color: rgba(99, 102, 241, 0.4);
    }

    /* Filters Builder */
    .flex-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .add-sub-btn {
      padding: 6px 14px;
      background: rgba(99, 102, 241, 0.1);
      border: 1px dashed rgba(99, 102, 241, 0.3);
      border-radius: 8px;
      color: #a5b4fc;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .add-sub-btn:hover {
      background: rgba(99, 102, 241, 0.2);
      border-color: #6366f1;
      color: white;
    }

    .empty-filters {
      font-size: 13px;
      color: #64748b;
      margin: 4px 0;
      font-style: italic;
    }

    .filters-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .filter-row {
      display: flex;
      align-items: center;
      gap: 12px;
      background: rgba(15, 23, 42, 0.4);
      padding: 10px 16px;
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.03);
    }

    .form-select.sm, .form-input.sm {
      padding: 6px 12px;
      font-size: 13px;
    }

    .form-select.sm.operator {
      width: 100px;
      color: #a5b4fc;
      font-weight: 600;
      text-align: center;
    }

    .remove-btn {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 14px;
      padding: 6px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .remove-btn:hover {
      background: rgba(239, 68, 68, 0.1);
    }

    /* Grid Action buttons */
    .table-actions {
      display: flex;
      gap: 8px;
    }

    .action-btn-sm {
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: white;
      transition: all 0.2s ease;
    }

    .action-btn-sm:hover {
      background: rgba(255, 255, 255, 0.1);
    }

    .action-btn-sm.add {
      background: rgba(34, 197, 94, 0.1);
      border-color: rgba(34, 197, 94, 0.3);
      color: #4ade80;
    }

    .action-btn-sm.add:hover {
      background: rgba(34, 197, 94, 0.2);
    }

    .action-btn-sm.delete-selected {
      background: rgba(239, 68, 68, 0.1);
      border-color: rgba(239, 68, 68, 0.3);
      color: #fca5a5;
    }

    .action-btn-sm.delete-selected:hover {
      background: rgba(239, 68, 68, 0.2);
      border-color: #ef4444;
      color: #f8fafc;
    }

    /* Tables */
    .table-wrapper {
      overflow-x: auto;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.05);
      background: rgba(15, 23, 42, 0.4);
    }

    .grid-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      text-align: left;
    }

    .grid-table th {
      background: rgba(15, 23, 42, 0.8);
      color: #94a3b8;
      font-weight: 600;
      text-transform: uppercase;
      font-size: 10px;
      letter-spacing: 0.5px;
      padding: 12px 14px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    }

    .grid-table td {
      padding: 8px 10px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
      vertical-align: middle;
    }

    .grid-table tr.selected {
      background: rgba(99, 102, 241, 0.05);
    }

    /* Cell inputs */
    .cell-input, .cell-select {
      width: 100%;
      background: rgba(15, 23, 42, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 6px;
      padding: 6px 10px;
      color: white;
      outline: none;
      font-size: 13px;
      font-family: inherit;
      box-sizing: border-box;
    }

    .cell-input:focus, .cell-select:focus {
      border-color: #6366f1;
      background: rgba(15, 23, 42, 0.8);
    }

    .cell-input.center {
      text-align: center;
    }

    .cell-input.code {
      font-family: 'Fira Code', monospace;
      color: #38bdf8;
    }

    .indent-wrapper {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .indent-btn {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 4px;
      color: #94a3b8;
      cursor: pointer;
      font-size: 10px;
      width: 20px;
      height: 20px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .indent-btn:hover {
      color: white;
      background: rgba(255, 255, 255, 0.1);
    }

    .style-cell {
      display: flex;
      gap: 6px;
    }

    /* Column active toggles */
    .col-enable-toggles {
      display: flex;
      gap: 4px;
      flex-wrap: wrap;
    }

    .col-badge {
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: #64748b;
      cursor: pointer;
      font-size: 10px;
      font-weight: bold;
    }

    .col-badge.active {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border-color: rgba(34, 197, 94, 0.3);
    }

    /* Live Preview Styles */
    .spreadsheet-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    .spreadsheet-table th {
      background: rgba(15, 23, 42, 0.6);
      color: #94a3b8;
      padding: 10px 14px;
      font-size: 11px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    }

    .spreadsheet-table td {
      padding: 10px 14px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }

    .preview-col-label {
      font-size: 9px;
      color: #64748b;
      text-transform: none;
      margin-top: 2px;
    }

    .spreadsheet-table tr.row-style-header {
      background: rgba(27, 79, 114, 0.3);
      color: white;
      border-bottom: 2px solid #1B4F72;
    }

    .spreadsheet-table tr.row-style-section {
      background: rgba(214, 234, 248, 0.1);
      color: #a5b4fc;
    }

    .spreadsheet-table tr.row-style-total {
      background: rgba(235, 245, 251, 0.05);
      border-top: 1px solid rgba(255, 255, 255, 0.2);
      border-bottom: 2px double rgba(255, 255, 255, 0.3);
      font-weight: bold;
    }

    .spreadsheet-table tr.row-style-highlight {
      background: rgba(255, 220, 0, 0.05);
      color: #fbbf24;
    }

    .sticky-col {
      position: sticky;
      left: 0;
      background: #1e293b;
      z-index: 2;
    }

    .flag-dot {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: rgba(34, 197, 94, 0.2);
      color: #4ade80;
      font-weight: bold;
      font-size: 10px;
    }

    .flag-dash {
      color: #334155;
    }

    .row-type-badge {
      font-size: 9px;
      font-weight: 700;
      padding: 1px 4px;
      border-radius: 3px;
      text-transform: uppercase;
    }
    .row-type-badge.section { background: #1e293b; color: #cbd5e1; }
    .row-type-badge.data { background: rgba(56, 189, 248, 0.15); color: #38bdf8; }
    .row-type-badge.calc { background: rgba(34, 197, 94, 0.15); color: #4ade80; }
    .row-type-badge.blank { background: transparent; color: #475569; }

    /* Alerts */
    .alert {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 20px;
      border-radius: 12px;
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

    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .animate-fade-in {
      animation: fadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
  `]
})
export class ReportBuilderComponent implements OnInit {
  isNewReport = true;
  saving = signal(false);
  showPreview = signal(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  // DB Metadata
  dbTables: string[] = [];
  tableColumns: string[] = [];
  distinctValues: { [key: string]: string[] } = {};

  // Form Fields
  reportId = '';
  reportName = '';
  reportVersion = 1;
  status = 'draft';
  sourceTable = '';
  granularity = '';
  timeframeStart = '2022-01-01';
  timeframeEnd = 'today';
  timeframeToday = true;
  quickFiltersList: string[] = [];
  generalFilters: FilterCondition[] = [];

  // Rows and Columns Data Models
  rows: any[] = [];
  columns: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private reportService: ReportService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const id = params['id'];
      if (id && id !== 'new') {
        this.isNewReport = false;
        this.reportId = id;
        // ── Fire tables list + report config in PARALLEL ──────────────────────
        // Both requests are independent — forkJoin sends them simultaneously
        // so total wait = max(t_tables, t_config) instead of t_tables + t_config.
        forkJoin({
          tables: this.reportService.getTables(),
          config: this.reportService.getReportConfig(id, '2025-12-31')
        }).subscribe({
          next: ({ tables, config }) => {
            this.dbTables = tables;
            this.applyReportConfig(config);
          },
          error: () => {
            this.errorMessage.set('Failed to load report definition details.');
          }
        });
      } else {
        this.isNewReport = true;
        this.reportService.getTables().subscribe({
          next: (tbls) => { this.dbTables = tbls; }
        });
        this.initializeDefaultCatalog();
      }
    });
  }

  loadReportConfig(id: string): void {
    this.reportService.getReportConfig(id, '2025-12-31').subscribe({
      next: (data) => { this.applyReportConfig(data); },
      error: () => { this.errorMessage.set('Failed to load report definition details.'); }
    });
  }

  applyReportConfig(data: any): void {
        this.reportId = data.reportId;
        this.reportName = data.name;
        this.reportVersion = data.version || 1;
        this.status = data.status || 'draft';
        this.sourceTable = data.sourceTable || '';
        this.granularity = data.granularity || '';
        this.timeframeToday = data.timeframeToday !== null ? data.timeframeToday : true;
        this.timeframeStart = this.formatDateForInput(data.timeframeStart || '2022-01-01');
        if (this.timeframeToday) {
          this.timeframeEnd = 'today';
        } else {
          this.timeframeEnd = this.formatDateForInput(data.timeframeEnd || '2026-12-31');
        }
        this.quickFiltersList = data.quickFilters ? data.quickFilters.split(',').filter(Boolean) : [];
        
        try {
          this.generalFilters = data.generalFilters ? JSON.parse(data.generalFilters) : [];
        } catch(e) {
          this.generalFilters = [];
        }

        // Columns
        this.columns = (data.columns || []).map((c: any) => ({
          colId: c.colId,
          label: c.label,
          colType: c.colType,
          headerLayout: c.headerLayout || 'border',
          periodOffset: c.periodOffset,
          rollingN: c.rollingN,
          formulaExpr: c.formulaExpr,
          selected: false
        }));

        // Rows
        this.rows = (data.rows || []).map((r: any) => ({
          rowId: r.rowId,
          label: r.label,
          rowType: r.rowType,
          source: r.source,
          parentRowId: r.parentRowId || '',
          style: r.style || 'normal',
          indentLevel: r.indentLevel || 0,
          filterExpr: r.filterExpr || '',
          activeCols: Array.from(r.activeCols || []),
          selected: false
        }));

        if (this.sourceTable) {
          this.loadTableMetadata(this.sourceTable);
        }
  }

  initializeDefaultCatalog(): void {
    this.reportId = '';
    this.reportName = '';
    this.reportVersion = 1;
    this.sourceTable = '';
    this.granularity = '';
    this.timeframeStart = '2022-01-01';
    this.timeframeEnd = 'today';
    this.timeframeToday = true;
    this.quickFiltersList = [];
    this.generalFilters = [];

    // Default 5 columns
    this.columns = [
      { colId: 'C1', label: 'Previous Weeks', colType: 'WEEK', headerLayout: 'border', periodOffset: -1, rollingN: null, formulaExpr: '', selected: false },
      { colId: 'C2', label: 'WTD no.', colType: 'WEEK', headerLayout: 'bold', periodOffset: 0, rollingN: null, formulaExpr: '', selected: false },
      { colId: 'C3', label: 'Budget WTD', colType: 'WEEK', headerLayout: 'border', periodOffset: 0, rollingN: null, formulaExpr: '', selected: false }
    ];

    // Default 3 rows
    this.rows = [
      { rowId: 'R1', label: 'Report Header', rowType: 'section', source: '', parentRowId: '', style: 'section', indentLevel: 0, filterExpr: '', activeCols: ['C1', 'C2', 'C3'], selected: false },
      { rowId: 'R2', label: 'GBS gross', rowType: 'data', source: 'SUM(amount)', parentRowId: 'R1', style: 'normal', indentLevel: 1, filterExpr: 'lifecycle = 2', activeCols: ['C1', 'C2', 'C3'], selected: false },
      { rowId: 'R3', label: 'GBS net', rowType: 'data', source: 'SUM(amount)', parentRowId: 'R1', style: 'normal', indentLevel: 1, filterExpr: 'lifecycle = 10', activeCols: ['C1', 'C2', 'C3'], selected: false }
    ];
  }

  onTableChange(): void {
    if (!this.sourceTable) {
      this.tableColumns = [];
      this.granularity = '';
      return;
    }
    this.loadTableMetadata(this.sourceTable);
  }

  loadTableMetadata(table: string): void {
    this.reportService.getTableColumns(table).subscribe({
      next: (cols) => {
        this.tableColumns = cols;
      }
    });
  }

  loadFilterValues(filter: FilterCondition): void {
    if (!this.sourceTable || !filter.attribute) return;
    this.reportService.getDistinctValues(this.sourceTable, filter.attribute).subscribe({
      next: (vals) => {
        this.distinctValues[filter.attribute] = vals;
      }
    });
  }

  getDistinctOptions(attribute: string): string[] {
    return this.distinctValues[attribute] || [];
  }

  // Quick filters helpers
  isQuickFilter(col: string): boolean {
    return this.quickFiltersList.includes(col);
  }

  toggleQuickFilter(col: string): void {
    const idx = this.quickFiltersList.indexOf(col);
    if (idx === -1) {
      this.quickFiltersList.push(col);
    } else {
      this.quickFiltersList.splice(idx, 1);
    }
  }

  onTimeframeTodayToggle(): void {
    if (this.timeframeToday) {
      this.timeframeEnd = 'today';
    } else {
      const d = new Date();
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      this.timeframeEnd = `${yyyy}-${mm}-${dd}`;
    }
  }

  formatDateForInput(dateStr: string): string {
    if (!dateStr) return '';
    const trimmed = dateStr.trim().toLowerCase();
    if (trimmed === 'today' || trimmed === 'sysdate') {
      return '';
    }
    
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
      return dateStr;
    }

    const parts = dateStr.split('/');
    if (parts.length === 3) {
      const month = parts[0].padStart(2, '0');
      const day = parts[1].padStart(2, '0');
      let year = parts[2];
      if (year.length === 2) {
        year = '20' + year;
      }
      return `${year}-${month}-${day}`;
    }

    const d = new Date(dateStr);
    if (!isNaN(d.getTime())) {
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd}`;
    }
    return '';
  }

  // General filters builder
  addGeneralFilter(): void {
    this.generalFilters.push({ attribute: '', operator: 'is', value: '' });
  }

  removeGeneralFilter(index: number): void {
    this.generalFilters.splice(index, 1);
  }

  // Grid rows helpers
  addRow(): void {
    const nextNum = this.rows.length + 1;
    this.rows.push({
      rowId: `R${nextNum}`,
      label: `New Row ${nextNum}`,
      rowType: 'data',
      source: 'SUM(amount)',
      parentRowId: '',
      style: 'normal',
      indentLevel: 0,
      filterExpr: '',
      activeCols: this.columns.map(c => c.colId),
      selected: false
    });
  }

  resetRows(): void {
    if (confirm('Are you sure you want to reset all rows?')) {
      this.rows = [];
    }
  }

  deleteRow(index: number): void {
    if (confirm(`Are you sure you want to delete row "${this.rows[index].label || this.rows[index].rowId}"?`)) {
      this.rows.splice(index, 1);
    }
  }

  deleteSelectedRows(): void {
    const selectedCount = this.rows.filter(r => r.selected).length;
    if (selectedCount === 0) {
      alert('Please select at least one row to delete.');
      return;
    }
    if (confirm(`Are you sure you want to delete the ${selectedCount} selected row(s)?`)) {
      this.rows = this.rows.filter(r => !r.selected);
    }
  }

  duplicateSelectedRow(): void {
    const selectedRows = this.rows.filter(r => r.selected);
    if (selectedRows.length === 0) {
      alert('Please select at least one row to duplicate.');
      return;
    }

    selectedRows.forEach(sr => {
      const copyId = `R${this.rows.length + 1}`;
      this.rows.push({
        ...sr,
        rowId: copyId,
        label: `${sr.label} (Copy)`,
        selected: false
      });
    });
  }

  reorderRows(): void {
    this.rows.sort((a, b) => {
      const aNum = parseInt(a.rowId.replace(/\\D/g, '')) || 0;
      const bNum = parseInt(b.rowId.replace(/\\D/g, '')) || 0;
      return aNum - bNum;
    });
  }

  toggleAllRowsSelect(event: any): void {
    const checked = event.target.checked;
    this.rows.forEach(r => r.selected = checked);
  }

  changeIndent(row: any, diff: number): void {
    row.indentLevel = Math.max(0, (row.indentLevel || 0) + diff);
  }

  toggleColForRow(row: any, colId: string): void {
    const cid = colId.toUpperCase();
    const idx = row.activeCols.indexOf(cid);
    if (idx === -1) {
      row.activeCols.push(cid);
    } else {
      row.activeCols.splice(idx, 1);
    }
  }

  // Grid columns helpers
  addColumn(): void {
    const nextNum = this.columns.length + 1;
    this.columns.push({
      colId: `C${nextNum}`,
      label: `Column ${nextNum}`,
      colType: 'WEEK',
      headerLayout: 'border',
      periodOffset: 0,
      rollingN: null,
      formulaExpr: '',
      selected: false
    });
  }

  resetColumns(): void {
    if (confirm('Are you sure you want to reset all columns?')) {
      this.columns = [];
    }
  }

  deleteColumn(index: number): void {
    const colToDelete = this.columns[index];
    if (confirm(`Are you sure you want to delete column "${colToDelete.label || colToDelete.colId}"?`)) {
      const colIdUpper = colToDelete.colId.toUpperCase();
      this.columns.splice(index, 1);
      // Clean up active column mapping references in row definitions
      this.rows.forEach(row => {
        if (row.activeCols) {
          row.activeCols = row.activeCols.filter((cid: string) => cid.toUpperCase() !== colIdUpper);
        }
      });
    }
  }

  deleteSelectedCols(): void {
    const selectedCols = this.columns.filter(c => c.selected);
    const selectedCount = selectedCols.length;
    if (selectedCount === 0) {
      alert('Please select at least one column to delete.');
      return;
    }
    if (confirm(`Are you sure you want to delete the ${selectedCount} selected column(s)?`)) {
      const colIdsUpper = selectedCols.map(c => c.colId.toUpperCase());
      this.columns = this.columns.filter(c => !c.selected);
      // Clean up active column mapping references in row definitions
      this.rows.forEach(row => {
        if (row.activeCols) {
          row.activeCols = row.activeCols.filter((cid: string) => !colIdsUpper.includes(cid.toUpperCase()));
        }
      });
    }
  }

  duplicateSelectedColumn(): void {
    const selectedCols = this.columns.filter(c => c.selected);
    if (selectedCols.length === 0) {
      alert('Please select at least one column to duplicate.');
      return;
    }

    selectedCols.forEach(sc => {
      const copyId = `C${this.columns.length + 1}`;
      this.columns.push({
        ...sc,
        colId: copyId,
        label: `${sc.label} (Copy)`,
        selected: false
      });
    });
  }

  reorderColumns(): void {
    this.columns.sort((a, b) => {
      const aNum = parseInt(a.colId.replace(/\\D/g, '')) || 0;
      const bNum = parseInt(b.colId.replace(/\\D/g, '')) || 0;
      return aNum - bNum;
    });
  }

  toggleAllColsSelect(event: any): void {
    const checked = event.target.checked;
    this.columns.forEach(c => c.selected = checked);
  }

  // Global actions
  togglePreview(): void {
    this.showPreview.set(!this.showPreview());
  }

  saveConfig(): void {
    if (!this.reportId || !this.reportName) {
      this.errorMessage.set('Report ID and Report Title are mandatory fields.');
      return;
    }
    if (!this.sourceTable) {
      this.errorMessage.set('Source Table is required.');
      return;
    }

    this.saving.set(true);
    this.successMessage.set(null);
    this.errorMessage.set(null);

    // Format DTO payload
    const payload = {
      reportId: this.reportId,
      name: this.reportName,
      exploreId: 1, // Default fallback
      status: this.status,
      sourceTable: this.sourceTable,
      granularity: this.granularity,
      timeframeStart: this.timeframeStart,
      timeframeEnd: this.timeframeToday ? 'today' : this.timeframeEnd,
      timeframeToday: this.timeframeToday,
      quickFilters: this.quickFiltersList.join(','),
      generalFilters: JSON.stringify(this.generalFilters),
      columns: this.columns.map((c, idx) => ({
        colId: c.colId,
        label: c.label,
        colType: c.colType,
        periodOffset: c.periodOffset || 0,
        rollingN: c.rollingN,
        formulaExpr: c.formulaExpr,
        displayOrder: idx + 1
      })),
      rows: this.rows.map((r, idx) => ({
        rowId: r.rowId,
        reportId: this.reportId,
        label: r.label,
        rowType: r.rowType,
        source: r.source,
        parentRowId: r.parentRowId || null,
        style: r.style || 'normal',
        indentLevel: r.indentLevel,
        displayOrder: idx + 1,
        activeCols: r.activeCols,
        filterExpr: r.filterExpr
      }))
    };

    const request$ = this.isNewReport 
      ? this.reportService.createReport(payload)
      : this.reportService.saveReport(this.reportId, payload);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Report definition successfully saved to database!');
        setTimeout(() => {
          this.router.navigate(['/reports', this.reportId]);
        }, 1200);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(err.error?.message || 'Failed to persist report definition in the database.');
      }
    });
  }

  goBack(): void {
    if (confirm('Discard changes and return to catalog?')) {
      if (this.isNewReport) {
        this.router.navigate(['/dashboard']);
      } else {
        this.router.navigate(['/reports', this.reportId]);
      }
    }
  }
}
