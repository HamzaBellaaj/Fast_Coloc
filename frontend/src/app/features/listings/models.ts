export interface ListingPhoto {
  id: string;
  url: string;
  caption: string;
  display_order: number;
}
export interface Listing {
  id: string;
  author_id: string;
  kind: 'OFFER' | 'REQUEST';
  title: string;
  description: string;
  locality_id: number;
  locality: string;
  governorate_id: number;
  governorate: string;
  street: string;
  latitude: number | null;
  longitude: number | null;
  rent: number;
  capacity: number;
  property_type: string;
  roommate_type: string;
  equipment: string[];
  available_from: string;
  available_until: string | null;
  publication_days: number;
  status: 'DRAFT' | 'PUBLISHED';
  is_hidden?: boolean;
  published_at: string | null;
  expires_at: string | null;
  golden?: boolean;
  golden_until?: string | null;
  version: number;
  favorite: boolean;
  photos: ListingPhoto[];
}
export interface ListingPage {
  items: Listing[];
  total: number;
  page: number;
  size: number;
}
export type ListingMarker = Pick<
  Listing,
  'id' | 'kind' | 'title' | 'rent' | 'latitude' | 'longitude' | 'golden'
>;
export interface ListingMapPage {
  items: ListingMarker[];
  total: number;
  page: number;
  size: number;
}
export interface Area {
  id: number;
  locality: string;
  governorate_id: number;
  governorate: string;
}
