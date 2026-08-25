import { Component, ElementRef, OnDestroy, afterNextRender, effect, input, viewChild } from '@angular/core';
import type * as L from 'leaflet';
import type { RegsStation } from '../../../core/models';

interface MapMarker {
  lat: number;
  lng: number;
  label: string;
  imageUrl?: string;
  date?: string;
}

function buildPopup(m: MapMarker): HTMLElement {
  const el = document.createElement('div');
  el.style.cssText = 'text-align:center;min-width:160px';

  if (m.imageUrl) {
    const img = document.createElement('img');
    img.src = m.imageUrl;
    img.style.cssText = 'width:160px;height:110px;object-fit:cover;border-radius:6px;display:block;margin-bottom:6px';
    el.appendChild(img);
  }

  const name = document.createElement('div');
  name.style.cssText = 'font-weight:600;font-size:13px;color:#111';
  name.textContent = m.label;
  el.appendChild(name);

  if (m.date) {
    const dateEl = document.createElement('div');
    dateEl.style.cssText = 'color:#888;font-size:11px;margin-top:2px';
    dateEl.textContent = m.date;
    el.appendChild(dateEl);
  }

  return el;
}

/**
 * Angular twin of components/ObservationMap.tsx. Leaflet is an imperative,
 * non-reactive DOM library in both versions, so both components boil down
 * to the same shape: create it once against a raw element, then push
 * updates into it by hand whenever inputs change — a chart/map library
 * like this is exactly the case neither framework's declarative rendering
 * model covers well, so both reach for the same escape hatch (useRef /
 * ElementRef) the same way.
 *
 * - React's mount-only `useEffect(() => {...}, [])` becomes `afterNextRender`
 *   — Angular's hook for "the view is now in the real DOM," the direct
 *   analog of an empty-dependency-array effect used for first-time setup.
 * - React's per-dependency `useEffect(fn, [markers])` / `useEffect(fn,
 *   [zonesGeoJson, stations])` become two more `effect()`s below, each
 *   auto-tracking the signal inputs it reads instead of a manual array.
 * - React's cleanup-function `return () => map.remove()` becomes the
 *   `ngOnDestroy` lifecycle method.
 *
 * One difference worth calling out: React injects a `<link rel=
 * "stylesheet">` for leaflet.css straight into this component's JSX — a
 * workable but slightly unusual trick for CSS-from-an-npm-package.
 * Angular's normal answer is registering the same file once in
 * angular.json's global `styles` array (see that file) rather than a
 * component reaching into `<head>` on every render.
 */
@Component({
  selector: 'app-observation-map',
  template: `<div #mapEl [style.height]="height()" style="border-radius: 8px; overflow: hidden; z-index: 0"></div>`,
})
export class ObservationMap implements OnDestroy {
  markers = input.required<MapMarker[]>();
  height = input('300px');
  // Quebec fishing zone polygons + consumption-advisory stations — optional, toggle-able overlay.
  zonesGeoJson = input<GeoJSON.FeatureCollection | undefined>();
  stations = input<RegsStation[] | undefined>();

  private mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');
  private mapInstance: L.Map | null = null;
  private regsLayer: L.LayerGroup | null = null;

  constructor() {
    afterNextRender(() => {
      import('leaflet').then((L) => {
        // @ts-expect-error _getIconUrl is internal
        delete L.Icon.Default.prototype._getIconUrl;
        L.Icon.Default.mergeOptions({
          iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
          iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
          shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
          iconSize: [25, 41],
          iconAnchor: [12, 41],
          popupAnchor: [1, -34],
          shadowSize: [41, 41],
          shadowAnchor: [12, 41],
        });

        const markers = this.markers();
        const center: [number, number] = markers.length > 0 ? [markers[0].lat, markers[0].lng] : [20, 0];
        const zoom = markers.length > 0 ? 5 : 2;

        const map = L.map(this.mapEl().nativeElement).setView(center, zoom);
        this.mapInstance = map;

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
          maxZoom: 18,
        }).addTo(map);

        markers.forEach((m) => {
          L.marker([m.lat, m.lng]).addTo(map).bindPopup(buildPopup(m));
        });
      });
    });

    // Update markers when they change.
    effect(() => {
      const markers = this.markers();
      if (!this.mapInstance) return;
      import('leaflet').then((L) => {
        const map = this.mapInstance!;
        map.eachLayer((layer) => {
          if (layer instanceof L.Marker) map.removeLayer(layer);
        });
        markers.forEach((m) => {
          L.marker([m.lat, m.lng]).addTo(map).bindPopup(buildPopup(m));
        });
        if (markers.length > 0) {
          map.setView([markers[0].lat, markers[0].lng], 5);
        }
      });
    });

    // Quebec fishing zones + consumption-advisory stations — a toggle-able
    // overlay, added/removed reactively as a single layer group.
    effect(() => {
      const zonesGeoJson = this.zonesGeoJson();
      const stations = this.stations();
      if (!this.mapInstance) return;
      import('leaflet').then((L) => {
        const map = this.mapInstance!;
        if (this.regsLayer) {
          map.removeLayer(this.regsLayer);
          this.regsLayer = null;
        }
        if (!zonesGeoJson && !stations?.length) return;

        const group = L.layerGroup();
        if (zonesGeoJson) {
          L.geoJSON(zonesGeoJson, {
            style: { color: '#2563eb', weight: 1, fillOpacity: 0.05 },
            onEachFeature: (feature, layer) => {
              const name = feature.properties?.['zone_name'] ?? feature.properties?.['name'];
              if (name) layer.bindTooltip(String(name));
            },
          }).addTo(group);
        }
        (stations ?? []).forEach((s) => {
          L.circleMarker([s.latitude, s.longitude], {
            radius: 5,
            color: '#16a34a',
            fillColor: '#16a34a',
            fillOpacity: 0.8,
          })
            .bindTooltip(s.hydronyme)
            .addTo(group);
        });
        group.addTo(map);
        this.regsLayer = group;
      });
    });
  }

  ngOnDestroy(): void {
    this.mapInstance?.remove();
    this.mapInstance = null;
  }
}
