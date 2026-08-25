import { Component } from '@angular/core';
import { RegsChat } from './regs-chat/regs-chat';

/** Angular twin of app/regs/page.tsx — a one-line wrapper in both versions. */
@Component({
  selector: 'app-regs-page',
  imports: [RegsChat],
  template: `<app-regs-chat />`,
})
export class RegsPage {}
