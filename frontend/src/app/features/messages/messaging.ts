import { inject, Injectable } from '@angular/core';
import { Api } from '../../core/api';
export interface Conversation {
  id: string;
  listing_id: string | null;
  listing_reference: string;
  listing_title: string;
  listing_kind: 'OFFER' | 'REQUEST';
  locality: string;
  rent: number;
  participant_id: string;
  participant_name: string;
  last_message: string;
  unread_count: number;
  listing_available: boolean;
  exchange_available?: boolean;
  created_at: string;
  updated_at: string;
}
export interface Message {
  id: number;
  conversation_id: string;
  sender_id: string;
  client_id: string;
  content: string;
  created_at: string;
}
export interface History {
  items: Message[];
  hasMore: boolean;
  nextBefore: number | null;
}
@Injectable({ providedIn: 'root' })
export class Messaging {
  private api = inject(Api);
  inbox(page = 0) {
    return this.api.request<{ items: Conversation[]; total: number; page: number; size: number }>(
      'GET',
      '/conversations?page=' + page,
    );
  }
  async lookup(listingId: string) {
    const result = await this.api.request<{ items: Conversation[] }>(
      'GET',
      '/conversations?listingId=' + encodeURIComponent(listingId),
    );
    return result.items[0] ?? null;
  }
  detail(id: string) {
    return this.api.request<Conversation>('GET', '/conversations/' + id);
  }
  history(id: string, before?: number) {
    return this.api.request<History>(
      'GET',
      '/conversations/' + id + '/messages' + (before ? '?before=' + before : ''),
    );
  }
  start(listingId: string, content: string, clientId: string) {
    return this.api.request<Conversation>('POST', '/conversations', {
      listingId,
      content,
      clientId,
    });
  }
  send(id: string, content: string, clientId: string) {
    return this.api.request<Message>('POST', '/conversations/' + id + '/messages', {
      content,
      clientId,
    });
  }
  read(id: string, throughId: number) {
    return this.api.request<void>('PUT', '/conversations/' + id + '/read', { throughId });
  }
}
