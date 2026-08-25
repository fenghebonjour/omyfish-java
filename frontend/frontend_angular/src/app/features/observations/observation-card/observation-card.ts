import { Component, computed, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import type { ObservationDto } from '../../../core/models';
import { BiteScorePanel } from '../bite-score-panel/bite-score-panel';

/** Angular twin of the local `ObservationCard` function inside app/observations/page.tsx. */
@Component({
  selector: 'app-observation-card',
  imports: [DatePipe, BiteScorePanel],
  templateUrl: './observation-card.html',
})
export class ObservationCard {
  obs = input.required<ObservationDto>();
  delete = output<string>();

  showBiteScore = signal(false);
  imageFailed = signal(false);

  confidencePercent = computed(() => Math.round(this.obs().topConfidence * 100));
  barColor = computed(() => {
    const c = this.confidencePercent();
    return c >= 85 ? 'bg-green-500' : c >= 50 ? 'bg-yellow-400' : 'bg-red-400';
  });
  hasLocation = computed(() => this.obs().latitude != null && this.obs().longitude != null);
}
