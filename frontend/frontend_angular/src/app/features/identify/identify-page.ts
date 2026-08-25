import { Component } from '@angular/core';
import { FishUploader } from './fish-uploader/fish-uploader';

/** Angular twin of app/identify/page.tsx. */
@Component({
  selector: 'app-identify-page',
  imports: [FishUploader],
  templateUrl: './identify-page.html',
})
export class IdentifyPage {}
