# Headless BI Reporting Platform — Angular Frontend

This is a modern, responsive single-page application (SPA) built using **Angular 21 (standalone components)**. It acts as the interactive user interface for the Headless BI Excel Reporting Engine, connecting to the Spring Boot Java backend.

The interface features a **premium glassmorphic dark-mode design** with customized animations, layout structures, and real-time report configuration grids.

---

## 🎨 Key Features & Components

The application is structured into standalone components routed dynamically under `src/app/app.routes.ts`:

1. **Sign In (`/login`)** — [LoginComponent](file:///g:/workspace/ReportTemplate/frontend/src/app/components/login.ts):
   * Secure interface validating user authentication.
   * Leverages Basic Auth headers via [AuthService](file:///g:/workspace/ReportTemplate/frontend/src/app/services/auth.service.ts).
   * Default credentials: `admin` / `password`.

2. **Reports Catalog (`/dashboard`)** — [DashboardComponent](file:///g:/workspace/ReportTemplate/frontend/src/app/components/dashboard.ts):
   * Browse a catalog of active and draft report templates loaded in the database metadata tables.
   * Upload and ingest new Excel templates (`.xlsx`) directly using drag-and-drop or file selection.
   * Auto-refreshes the catalog dynamically upon template ingestion.

3. **Template Visualizer (`/reports/:id`)** — [ReportDetailComponent](file:///g:/workspace/ReportTemplate/frontend/src/app/components/report-detail.ts):
   * View full details of the columns (types: `DATE`, `DATA`, `CALC`) and rows (types: `label`, `data`, `calc`) for a specific report layout.
   * Select a Reference Date (e.g., `2025-12-31`) and run the reporting engine.
   * Downloads the compiled, calculated, and POI-styled spreadsheet directly to the user's local downloads folder.

4. **Semantic Layer Browser (`/semantic`)** — [SemanticViewerComponent](file:///g:/workspace/ReportTemplate/frontend/src/app/components/semantic.ts):
   * Inspect the logical metadata definitions in the semantic registry.
   * Browse **Explores & Joins** to see how fact and dimension tables are logically mapped together (e.g., join conditions).
   * Browse **Views & Schema Mapping** to inspect defined **Dimensions** (with physical column names) and **Measures** (with SQL aggregation expressions and types).

---

## ⚙️ Development Guide

### Prerequisites
* **Node.js**: v18+ / v20+ recommended
* **NPM**: v9+ (uses package manager NPM v11.8.0)

### Setup & Installation
1. Navigate into the frontend folder:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```

### Running Locally
To launch the Angular development server:
```bash
npm start
```
The server will start at **[http://localhost:4200/](http://localhost:4200/)**. The page will automatically reload if you change any of the source files.

### Backend Connection
The application connects to the Spring Boot REST API at **`http://localhost:8101/api/reports`**.
Ensure the backend server is running in parallel.

### Building
To build the optimized production package:
```bash
npm run build
```
Build artifacts will be written to `dist/frontend/`.

### Unit Testing
To run tests using Angular test utility tools:
```bash
npm test
```

---

## 📂 Project Structure

```
frontend/src/app/
├── components/          # Standalone view components
│   ├── login.ts         # User auth view
│   ├── dashboard.ts     # Main catalog page & spreadsheet uploader
│   ├── report-detail.ts # In-depth layout visualizer & run executor
│   └── semantic.ts      # LookML-equivalent metadata registry browser
├── guards/
│   └── auth.guard.ts    # Route guard to block unauthenticated access
├── services/
│   ├── auth.service.ts  # Session storage & authorization header manager
│   └── report.service.ts# HTTP REST client mapping to the Spring Boot API
├── app.config.ts        # Application providers (HttpClient, Router, etc.)
├── app.html             # Shell containing <router-outlet>
├── app.routes.ts        # Standalone routing table definition
└── app.ts               # Core app component bootloader
```
