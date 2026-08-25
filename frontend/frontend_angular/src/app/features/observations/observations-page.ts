import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import type { ObservationDto, RegsStation } from '../../core/models';
import { ObservationMap } from './observation-map/observation-map';
import { ObservationCard } from './observation-card/observation-card';
import { errorMessage } from '../../core/http-error.util';

/** Angular twin of app/observations/page.tsx — protected by authGuard, see admin-page.ts. */
@Component({
  selector: 'app-observations-page',
  imports: [RouterLink, ObservationMap, ObservationCard],
  templateUrl: './observations-page.html',
})
export class ObservationsPage {
  private auth = inject(AuthService);
  private api = inject(ApiService);

  observations = signal<ObservationDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  showRegsLayer = signal(false);
  zonesGeoJson = signal<GeoJSON.FeatureCollection | undefined>(undefined);
  stations = signal<RegsStation[] | undefined>(undefined);

  constructor() {
    firstValueFrom(this.api.observations.getAll(this.auth.token()!, true))
      .then((obs) => this.observations.set(obs))
      .catch((e) => this.error.set(errorMessage(e)))
      .finally(() => this.loading.set(false));
  }

  async handleDelete(id: string): Promise<void> {
    if (!confirm('Delete this observation?')) return;
    try {
      await firstValueFrom(this.api.observations.delete(id, this.auth.token()!));
      this.observations.update((prev) => prev.filter((o) => o.id !== id));
    } catch (e) {
      alert('Delete failed: ' + errorMessage(e));
    }
  }

  mapMarkers = computed(() =>
    this.observations()
      .filter((o) => o.latitude != null && o.longitude != null)
      .map((o) => ({
        lat: o.latitude!,
        lng: o.longitude!,
        label: o.speciesName,
        imageUrl: o.imageUrl,
        date: new Date(o.observedAt).toLocaleDateString(),
      })),
  );

  async toggleRegsLayer(): Promise<void> {
    const next = !this.showRegsLayer();
    this.showRegsLayer.set(next);
    if (next && !this.zonesGeoJson()) {
      const first = this.mapMarkers()[0];
      const [zones, nearbyStations] = await Promise.all([
        firstValueFrom(this.api.regs.zonesGeoJson()),
        first ? firstValueFrom(this.api.regs.consumptionStations(first.lat, first.lng)) : Promise.resolve([]),
      ]);
      this.zonesGeoJson.set(zones);
      this.stations.set(nearbyStations);
    }
  }
}
