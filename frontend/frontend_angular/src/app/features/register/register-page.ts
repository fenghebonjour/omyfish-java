import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/http-error.util';

/** Angular twin of app/register/page.tsx — see login-page.ts for the ngModel-vs-useState note. */
@Component({
  selector: 'app-register-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './register-page.html',
})
export class RegisterPage {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private router = inject(Router);

  email = '';
  displayName = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  async handleSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    this.loading.set(true);
    try {
      await firstValueFrom(this.api.auth.register(this.email, this.password, this.displayName || undefined));
      // Auto-login after successful registration
      await this.auth.login(this.email, this.password);
      this.router.navigateByUrl('/');
    } catch (err) {
      this.error.set(errorMessage(err, 'Registration failed'));
    } finally {
      this.loading.set(false);
    }
  }
}
