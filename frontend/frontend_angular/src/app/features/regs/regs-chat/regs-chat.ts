import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { marked } from 'marked';
import { ApiService } from '../../../core/api.service';
import { errorMessage } from '../../../core/http-error.util';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  sources?: string[];
}

/**
 * Angular twin of components/RegsChat.tsx. `input` bound with `[(ngModel)]`
 * instead of value/onChange (see login-page.ts); `(keydown.enter)` replaces
 * `onKeyDown={e => e.key === 'Enter' && send()}` — Angular has dedicated
 * dot-suffix event filters (`keydown.enter`, `keydown.escape`, ...) for the
 * single-key checks React has to write out by hand in the handler body.
 */
@Component({
  selector: 'app-regs-chat',
  imports: [FormsModule],
  templateUrl: './regs-chat.html',
})
export class RegsChat {
  private api = inject(ApiService);

  messages = signal<ChatMessage[]>([]);
  input = '';
  sending = signal(false);
  error = signal<string | null>(null);

  /**
   * React renders markdown as a component tree (react-markdown); Angular has
   * no equivalent, so this parses to an HTML string with `marked` and binds
   * it via `[innerHTML]` in the template. Angular sanitizes `[innerHTML]`
   * bindings by default (stripping script tags, event handler attributes,
   * etc.) as long as the string isn't wrapped in `bypassSecurityTrustHtml` —
   * left alone here on purpose so that sanitization stays on.
   */
  renderMarkdown(text: string): string {
    return marked.parse(text, { async: false });
  }

  async send(): Promise<void> {
    const question = this.input.trim();
    if (!question || this.sending()) return;
    this.input = '';
    this.error.set(null);
    this.messages.update((prev) => [...prev, { role: 'user', text: question }]);
    this.sending.set(true);
    try {
      const response = await firstValueFrom(this.api.regs.ask(question));
      this.messages.update((prev) => [...prev, { role: 'assistant', text: response.answer, sources: response.sources }]);
    } catch (e) {
      this.error.set(errorMessage(e, 'Failed to get an answer'));
    } finally {
      this.sending.set(false);
    }
  }
}
