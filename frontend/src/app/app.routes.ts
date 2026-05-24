import { Routes } from '@angular/router';
import { LoginComponent } from './components/login';
import { DashboardComponent } from './components/dashboard';
import { ReportDetailComponent } from './components/report-detail';
import { SemanticViewerComponent } from './components/semantic';
import { ReportBuilderComponent } from './components/report-builder';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'reports/new/edit', component: ReportBuilderComponent, canActivate: [authGuard] },
  { path: 'reports/:id/edit', component: ReportBuilderComponent, canActivate: [authGuard] },
  { path: 'reports/:id', component: ReportDetailComponent, canActivate: [authGuard] },
  { path: 'semantic', component: SemanticViewerComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];
