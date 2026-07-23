import {Routes} from '@angular/router';
import {authGuard, roleGuard} from './core/auth.guards';
import {completedOnboardingGuard} from './core/onboarding.guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login.page').then(m => m.LoginPage) },
  { path: 'onboarding', canActivate: [authGuard], loadComponent: () => import('./features/onboarding.page').then(m => m.OnboardingPage) },
  { path: 'catalog', canActivate: [authGuard, completedOnboardingGuard], loadComponent: () => import('./features/catalog.page').then(m => m.CatalogPage) },
  { path: 'catalog/:versionId', canActivate: [authGuard, completedOnboardingGuard], loadComponent: () => import('./features/catalog-detail.page').then(m => m.CatalogDetailPage) },
  { path: 'admin/exercise-import', canActivate: [authGuard, completedOnboardingGuard, roleGuard('CONTENT_ADMIN')], loadComponent: () => import('./features/exercise-import.page').then(m => m.ExerciseImportPage) },
  {
    path: 'admin/exercise-import/batches/:batchId/attention',
    canActivate: [authGuard, completedOnboardingGuard, roleGuard('CONTENT_ADMIN')],
    loadComponent: () => import('./features/exercise-import-attention.page').then(m => m.ExerciseImportAttentionPage)
  },
  {
    path: 'admin/exercise-review',
    canActivate: [authGuard, completedOnboardingGuard, roleGuard('CONTENT_ADMIN')],
    loadComponent: () => import('./features/exercise-review.page').then(m => m.ExerciseReviewPage)
  },
  {
    path: 'admin/exercise-review/:versionId',
    canActivate: [authGuard, completedOnboardingGuard, roleGuard('CONTENT_ADMIN')],
    loadComponent: () => import('./features/exercise-review-detail.page').then(m => m.ExerciseReviewDetailPage)
  },
  { path: 'plan', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/plan.page').then(m => m.PlanPage) },
  { path: 'sessions', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/sessions.page').then(m => m.SessionsPage) },
  { path: 'reminders', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/reminder-preferences.page').then(m => m.ReminderPreferencesPage) },
  { path: 'specialist-alerts', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-alerts.page').then(m => m.SpecialistAlertsPage) },
  { path: 'gamification', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/gamification.page').then(m => m.GamificationPage) },
  { path: '', pathMatch: 'full', redirectTo: 'onboarding' },
  { path: '**', redirectTo: '' }
];
