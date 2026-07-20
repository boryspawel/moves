import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login.page').then(m => m.LoginPage) },
  { path: 'onboarding', canActivate: [authGuard], loadComponent: () => import('./features/onboarding.page').then(m => m.OnboardingPage) },
  { path: 'catalog', canActivate: [authGuard], loadComponent: () => import('./features/catalog.page').then(m => m.CatalogPage) },
  { path: 'plan', canActivate: [authGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/plan.page').then(m => m.PlanPage) },
  { path: 'sessions', canActivate: [authGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/sessions.page').then(m => m.SessionsPage) },
  { path: 'specialist-alerts', canActivate: [authGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-alerts.page').then(m => m.SpecialistAlertsPage) },
  { path: 'gamification', canActivate: [authGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/gamification.page').then(m => m.GamificationPage) },
  { path: '', pathMatch: 'full', redirectTo: 'onboarding' },
  { path: '**', redirectTo: '' }
];
