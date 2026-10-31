export interface MapTileConfig {
  url: string;
  options: {
    attribution: string;
    maxZoom?: number;
    subdomains?: string[];
    [key: string]: unknown;
  };
}

export function getMapTileConfig(): MapTileConfig {
  const provider = (import.meta.env.VITE_MAPS_PROVIDER || 'google').toLowerCase();
  const apiKey = (import.meta.env.VITE_MAPS_API_KEY || '').trim();

  switch (provider) {
    case 'geoapify': {
      // Free Geoapify demo/fallback key if none provided
      const key = apiKey || '6c478a5e0d37446bb38258dc7117830b';
      return {
        url: `https://maps.geoapify.com/v1/tile/osm-bright/{z}/{x}/{y}.png?apiKey=${key}`,
        options: {
          attribution: 'Powered by <a href="https://www.geoapify.com/" target="_blank" rel="noreferrer">Geoapify</a> | &copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a>',
          maxZoom: 20,
        },
      };
    }

    case 'osm':
      return {
        url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        options: {
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',
          maxZoom: 19,
          subdomains: ['a', 'b', 'c'],
        },
      };

    case 'voyager':
      return {
        url: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
        options: {
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
          maxZoom: 19,
          subdomains: ['a', 'b', 'c', 'd'],
        },
      };

    case 'google':
    default:
      return {
        url: 'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
        options: {
          attribution: '&copy; Google Maps',
          subdomains: ['0', '1', '2', '3'],
          maxZoom: 20,
        },
      };
  }
}
