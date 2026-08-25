import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http-error.util';

/**
 * Angular twin of app/login/page.tsx.
 *
 * React keeps each field as its own `useState` and wires
 * `value={email} onChange={e => setEmail(e.target.value)}` — a "controlled
 * input" pattern needed because React has no native two-way binding.
 * Angular's `[(ngModel)]` (FormsModule) *is* built-in two-way binding: one
 * directive does the same value-in/value-out job as React's value+onChange
 * pair. Note `email`/`password` are plain mutable fields here, not
 * signals — `[(ngModel)]` assigns straight into the bound property
 * (`email = $event`), which is exactly what a signal (a function you call,
 * not a variable you assign) can't be the target of. `error`/`loading`
 * stay signals since the template only *reads* them.
 */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './login-page.html',
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  async handleSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    this.loading.set(true);
    try {
      await this.auth.login(this.email, this.password);
      this.router.navigateByUrl('/');
    } catch (err) {
      this.error.set(errorMessage(err, 'Login failed'));
    } finally {
      this.loading.set(false);
    }
  }
}
