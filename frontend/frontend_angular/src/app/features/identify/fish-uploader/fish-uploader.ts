import { Component, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import exifr from 'exifr';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth.service';
import { ApiService } from '../../../core/api.service';
import type { RegsLimits, RegsConsumption } from '../../../core/models';
import { errorMessage } from '../../../core/http-error.util';
import { PredictionCard } from './prediction-card/prediction-card';
import { RegsLimitCard } from './regs-limit-card/regs-limit-card';
import { RegsConsumptionCard } from './regs-consumption-card/regs-consumption-card';

interface PredictionDto {
  speciesName: string;
  scientificName: string;
  confidence: number;
  confidencePercent: string;
  rank: number;
  conservationStatus?: string;
  habitat?: string;
  diet?: string;
  maxSizeCm?: number;
  description?: string;
  funFact?: string;
}

interface IdentifyFishResult {
  predictions: PredictionDto[];
  uncertain: boolean;
  imageKey: string;
  isFish?: boolean;
}

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/jpg'];
const MAX_SIZE = 20 * 1024 * 1024;

/**
 * Angular twin of components/FishUploader.tsx.
 *
 * Per this port's brief (raw libraries, no wrapper on either side), React's
 * `react-dropzone` hook is replaced with plain HTML5 drag-and-drop events
 * ((dragover)/(dragleave)/(drop)) plus a hidden `<input type="file">` for
 * the click-to-browse path — the same DOM APIs react-dropzone itself
 * wraps, just wired directly instead of through a hook that returns
 * spreadable prop bags (`getRootProps()`/`getInputProps()`).
 *
 * exifr is used exactly as in React — a plain JS library, no framework
 * binding needed on either side.
 *
 * Note this component talks to the backend with `HttpClient` directly
 * (not through `ApiService`), and to `/api/v1/observations` — a POST that
 * has no ApiService method at all. That mirrors the React source exactly:
 * FishUploader.tsx bypasses lib/api.ts's `identify`/`observations` helpers
 * and hand-rolls `fetch` calls instead, an inconsistency in the original
 * app that this port preserves rather than "fixes."
 */
@Component({
  selector: 'app-fish-uploader',
  imports: [RouterLink, PredictionCard, RegsLimitCard, RegsConsumptionCard],
  templateUrl: './fish-uploader.html',
})
export class FishUploader {
  auth = inject(AuthService);
  private api = inject(ApiService);
  private http = inject(HttpClient);

  isDragActive = signal(false);
  preview = signal<string | null>(null);
  loading = signal(false);
  result = signal<IdentifyFishResult | null>(null);
  error = signal<string | null>(null);
  saved = signal(false);
  saving = signal(false);
  lat = signal('');
  lng = signal('');
  regsLimits = signal<RegsLimits | null>(null);
  regsConsumption = signal<RegsConsumption | null>(null);
  regsLoading = signal(false);

  constructor() {
    // lat/lng resolve asynchronously (EXIF, then geolocation fallback)
    // after the identify result lands, so fetch regs info once both are
    // available — mirrors React's `useEffect(fn, [result, lat, lng])`.
    effect(() => {
      const result = this.result();
      const lat = this.lat();
      const lng = this.lng();
      if (!result || result.isFish === false || !lat || !lng) return;
      const species = result.predictions[0]?.speciesName;
      if (!species) return;

      this.regsLoading.set(true);
      Promise.allSettled([
        firstValueFrom(this.api.regs.limits(Number(lat), Number(lng), species)),
        firstValueFrom(this.api.regs.consumption(Number(lat), Number(lng), species)),
      ]).then(([limitsResult, consumptionResult]) => {
        this.regsLimits.set(limitsResult.status === 'fulfilled' ? limitsResult.value : null);
        this.regsConsumption.set(consumptionResult.status === 'fulfilled' ? consumptionResult.value : null);
        this.regsLoading.set(false);
      });
    });
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.handleFile(file);
  }

  onFileInputChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.handleFile(file);
    (event.target as HTMLInputElement).value = '';
  }

  private async handleFile(file: File): Promise<void> {
    if (!ACCEPTED_TYPES.includes(file.type) || file.size > MAX_SIZE) {
      this.error.set('Please choose a JPG, PNG or WEBP photo under 20 MB.');
      return;
    }

    this.preview.set(URL.createObjectURL(file));
    this.result.set(null);
    this.error.set(null);
    this.saved.set(false);
    this.lat.set('');
    this.lng.set('');
    this.regsLimits.set(null);
    this.regsConsumption.set(null);
    this.loading.set(true);

    try {
      const formData = new FormData();
      formData.append('image', file);
      formData.append('topK', '5');

      const token = this.auth.token();
      const data = await firstValueFrom(
        this.http.post<IdentifyFishResult>(`${environment.apiBase}/api/v1/species/identify`, formData, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        }),
      );
      this.result.set(data);

      const fallbackToGeolocation = () => {
        navigator.geolocation?.getCurrentPosition(
          (pos) => {
            this.lat.set(String(pos.coords.latitude));
            this.lng.set(String(pos.coords.longitude));
          },
          () => {},
        );
      };

      try {
        const parsed = await exifr.parse(file, {
          gps: true,
          tiff: true,
          xmp: false,
          iptc: false,
          icc: false,
          jfif: false,
          ihdr: false,
          translateValues: true,
          reviveValues: true,
        });
        const exifLat = parsed?.latitude;
        const exifLng = parsed?.longitude;
        if (exifLat != null && exifLng != null && !isNaN(exifLat) && !isNaN(exifLng)) {
          this.lat.set(String(exifLat));
          this.lng.set(String(exifLng));
        } else {
          fallbackToGeolocation();
        }
      } catch {
        fallbackToGeolocation();
      }
    } catch (err) {
      this.error.set(errorMessage(err, 'Identification failed'));
    } finally {
      this.loading.set(false);
    }
  }

  async handleSave(): Promise<void> {
    const result = this.result();
    const token = this.auth.token();
    if (!result || !token) return;
    this.saving.set(true);
    try {
      const top = result.predictions[0];
      // The image is already stored — identify uploaded it and returned
      // imageKey — so we just reference it here instead of re-uploading.
      const body = {
        speciesName: top.speciesName,
        scientificName: top.scientificName ?? '',
        topConfidence: top.confidence,
        imageStorageKey: result.imageKey,
        latitude: this.lat() ? Number(this.lat()) : null,
        longitude: this.lng() ? Number(this.lng()) : null,
      };

      await firstValueFrom(
        this.http.post(`${environment.apiBase}/api/v1/observations`, body, {
          headers: { Authorization: `Bearer ${token}` },
        }),
      );
      this.saved.set(true);
    } catch (err) {
      alert('Save failed: ' + errorMessage(err));
    } finally {
      this.saving.set(false);
    }
  }
}
