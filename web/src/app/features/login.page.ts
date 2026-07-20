import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [MatButtonModule, MatCardModule],
  template: `
    <mat-card class="panel">
      <mat-card-header><mat-card-title><h1>Zaloguj się do moves</h1></mat-card-title></mat-card-header>
      <mat-card-content>
        <p>Bezpieczne logowanie obsługuje Keycloak w przepływie Authorization Code z PKCE S256.</p>
        <p class="muted">Uprawnienia w nawigacji pomagają w obsłudze, ale każdą operację ponownie autoryzuje backend.</p>
      </mat-card-content>
      <mat-card-actions><button mat-flat-button type="button" (click)="login()">Przejdź do logowania</button></mat-card-actions>
    </mat-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  protected login(): void { void this.auth.login(); }
}
