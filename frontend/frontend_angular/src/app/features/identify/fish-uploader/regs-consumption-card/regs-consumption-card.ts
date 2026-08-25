import { Component, input } from '@angular/core';
import type { RegsConsumption } from '../../../../core/models';

@Component({
  selector: 'app-regs-consumption-card',
  templateUrl: './regs-consumption-card.html',
})
export class RegsConsumptionCard {
  consumption = input.required<RegsConsumption | null>();
  loading = input.required<boolean>();
}
