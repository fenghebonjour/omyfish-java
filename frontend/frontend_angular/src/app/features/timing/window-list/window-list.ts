import { Component, input } from '@angular/core';
import type { TimeWindow } from '../../../core/models';
import { formatWindow } from '../timing-format.util';

/**
 * The React page defines `WindowList` and `AlertBanner` as small local
 * function components living right inside app/page.tsx — JSX lets you
 * declare a component anywhere a function can go. Angular components are
 * always their own class + template pair, so this one gets pulled out to
 * its own file even though it's only used from TimingPage.
 */
@Component({
  selector: 'app-window-list',
  templateUrl: './window-list.html',
})
export class WindowList {
  title = input.required<string>();
  windows = input.required<TimeWindow[]>();
  formatWindow = formatWindow;
}
