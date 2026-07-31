import {Routes} from '@angular/router';
import {authGuard, roleGuard} from './core/auth.guards';
import {completedOnboardingGuard, rootLandingGuard} from './core/onboarding.guards';
import {RootLandingComponent} from './core/root-landing.component';

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
  { path: 'plan', pathMatch: 'full', redirectTo: 'exercise-sets' },
  { path: 'exercise-sets', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/exercise-sets/exercise-set-list.page').then(m => m.ExerciseSetListPage) },
  { path: 'exercise-sets/new', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/exercise-sets/exercise-set-editor.page').then(m => m.ExerciseSetEditorPage) },
  { path: 'exercise-sets/:exerciseSetId/versions/:versionId/edit', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/exercise-sets/exercise-set-editor.page').then(m => m.ExerciseSetEditorPage) },
  { path: 'exercise-sets/:exerciseSetId/versions/:versionId', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/exercise-sets/exercise-set-editor.page').then(m => m.ExerciseSetEditorPage) },
  { path: 'specialist/today', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-today.page').then(m => m.SpecialistTodayPage) },
  { path: 'specialist/clients', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-clients.page').then(m => m.SpecialistClientsPage) },
  { path: 'specialist/clients/:participantId', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-participant-workspace.page').then(m => m.SpecialistParticipantWorkspacePage) },
  { path: 'sessions', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/sessions.page').then(m => m.SessionsPage) },
  { path: 'reminders', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/reminder-preferences.page').then(m => m.ReminderPreferencesPage) },
  { path: 'specialist-alerts', canActivate: [authGuard, completedOnboardingGuard, roleGuard('SPECIALIST')], loadComponent: () => import('./features/specialist-alerts.page').then(m => m.SpecialistAlertsPage) },
  { path: 'gamification', canActivate: [authGuard, completedOnboardingGuard, roleGuard('PARTICIPANT')], loadComponent: () => import('./features/gamification.page').then(m => m.GamificationPage) },
  { path: '', pathMatch: 'full', canActivate: [rootLandingGuard], component: RootLandingComponent },
  { path: '**', redirectTo: '' }
];
