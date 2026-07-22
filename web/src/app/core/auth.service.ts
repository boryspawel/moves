import { Injectable, computed, signal } from '@angular/core';
import Keycloak, { KeycloakTokenParsed } from 'keycloak-js';
import { environment } from '../../environments/environment';

interface RealmToken extends KeycloakTokenParsed {
  realm_access?: { roles: string[] };
  preferred_username?: string;
  given_name?: string;
}

interface TokenProfile {
  firstName?: string;
  username?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly client = new Keycloak(environment.keycloak);
  private readonly authenticatedState = signal(false);
  private readonly profileState = signal<TokenProfile | null>(null);
  readonly ready = signal(false);
  readonly authenticated = computed(() => this.authenticatedState());
  readonly displayName = computed(() =>
    this.profileState()?.firstName ?? this.profileState()?.username ?? 'Użytkownik'
  );

  async initialize(): Promise<void> {
    try {
      const authenticated = await this.client.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`
      });
      this.authenticatedState.set(authenticated);
      if (authenticated) this.profileState.set(this.profileFromToken());
    } catch {
      this.authenticatedState.set(false);
    } finally {
      this.ready.set(true);
    }
  }

  private profileFromToken(): TokenProfile {
    const token = this.client.tokenParsed as RealmToken | undefined;
    return { firstName: token?.given_name, username: token?.preferred_username };
  }

  login(): Promise<void> {
    return this.client.login({ redirectUri: window.location.origin });
  }

  register(): Promise<void> {
    return this.client.register({ redirectUri: window.location.origin });
  }

  logout(): Promise<void> {
    return this.client.logout({ redirectUri: window.location.origin });
  }

  async accessToken(): Promise<string> {
    if (!this.client.authenticated) return '';
    await this.client.updateToken(30);
    return this.client.token ?? '';
  }

  hasRole(role: string): boolean {
    const token = this.client.tokenParsed as RealmToken | undefined;
    return token?.realm_access?.roles?.includes(role) ?? false;
  }
}
