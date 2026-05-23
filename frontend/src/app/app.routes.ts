import { Routes } from '@angular/router';
import { LoginComponent } from './components/login';
import { DashboardComponent } from './components/dashboard';
import { ReportDetailComponent } from './components/report-detail';
import { SemanticViewerComponent } from './components/semantic';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'reports/:id', component: ReportDetailComponent, canActivate: [authGuard] },
  { path: 'semantic', component: SemanticViewerComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];
