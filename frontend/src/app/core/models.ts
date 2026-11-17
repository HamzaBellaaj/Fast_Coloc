// DTOs checked against /v3/api-docs and backend StepRequests/ProfileDataService.
export interface Identity {
  firstName: string;
  lastName: string;
  gender?: string | null;
  age: number;
}
export interface Geography {
  countryCode: string;
  governorateId: number;
  localityId: number;
  street: string;
  postalCode: string;
}
export interface Personal {
  mainActivities: string[];
  hobbies: string[];
  nationalityCode: string | null;
  spokenLanguages: string[];
  religion: string | null;
  smoking: boolean | null;
  cleanliness: string | null;
  biography: string | null;
}
export interface Contacts {
  mobilePhone: string;
  residentialPhone: string;
  callAvailability: string;
  msnContact: string;
  skypeContact: string;
  websiteUrl: string;
}
export interface Preferences {
  minBudget: number | null;
  maxBudget: number | null;
  moveInDate: string | null;
  localityIds: number[];
}
export interface Profile {
  id: string;
  firstName: string | null;
  lastName: string | null;
  gender: string | null;
  age: number | null;
  role: 'USER' | 'ADMIN';
  status: string;
  completedAt: string | null;
  validatedSteps: number[];
}
export interface Photo {
  id: string;
  caption: string | null;
  mime_type: string;
  byte_size: number;
  display_order: number;
  is_primary: boolean;
}
export interface Details {
  profile: Profile;
  address: Partial<{
    country_code: string;
    governorate_id: number;
    locality_id: number;
    street: string;
    postal_code: string;
  }>;
  personal: Partial<{
    main_activities: string[];
    hobbies: string[];
    nationality_code: string;
    spoken_languages: string[];
    religion: string;
    smoking: boolean;
    cleanliness: string;
    biography: string;
  }>;
  contacts: Partial<{
    mobile_phone: string;
    residential_phone: string;
    call_availability: string;
    msn_contact: string;
    skype_contact: string;
    website_url: string;
  }>;
  preferences: Partial<{ min_budget: number; max_budget: number; move_in_date: string }>;
  preferredLocalities: number[];
  photos: Photo[];
}
export interface Country {
  code: string;
  name: string;
}
export interface Place {
  id: number;
  name: string;
}
export interface Session {
  access_token: string;
  refresh_token: string;
  expires_at?: number;
  expires_in: number;
  token_type: string;
}
export interface AdminUser {
  id: string;
  first_name: string | null;
  last_name: string | null;
  app_role: string;
  status: 'ACTIVE' | 'BLOCKED';
  completed_at: string | null;
  created_at: string;
  total_count: number;
}
