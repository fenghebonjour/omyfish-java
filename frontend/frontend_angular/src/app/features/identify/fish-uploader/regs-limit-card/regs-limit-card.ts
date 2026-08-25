import { Component, input } from '@angular/core';
import type { RegsLimits } from '../../../../core/models';

@Component({
  selector: 'app-regs-limit-card',
  templateUrl: './regs-limit-card.html',
})
export class RegsLimitCard {
  limits = input.required<RegsLimits | null>();
  loading = input.required<boolean>();
}
