import { Component, computed, input } from '@angular/core';
import type { PredictionDto } from '../../../../core/models';

const MEDALS = ['🥇', '🥈', '🥉'];

function conservationIcon(status: string): string {
  if (/Endangered/i.test(status)) return '🔴';
  if (/Vulnerable|Threatened/i.test(status)) return '🟡';
  return '🟢';
}

/** Angular twin of the local `PredictionCard` function inside components/FishUploader.tsx. */
@Component({
  selector: 'app-prediction-card',
  templateUrl: './prediction-card.html',
})
export class PredictionCard {
  prediction = input.required<PredictionDto>();

  pct = computed(() => Math.round(this.prediction().confidence * 100));
  barColor = computed(() => {
    const p = this.pct();
    return p >= 85 ? 'bg-green-500' : p >= 50 ? 'bg-yellow-400' : 'bg-red-400';
  });
  medal = computed(() => MEDALS[this.prediction().rank - 1] ?? '');
  conservationIcon = conservationIcon;
}
