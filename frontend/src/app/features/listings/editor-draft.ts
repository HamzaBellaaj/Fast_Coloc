import { Injectable } from '@angular/core';
import type { ListingEditor } from './listing-editor';
export interface EditorDraft {
  form: ListingEditor['form'];
  step: number;
  version: number | null;
  caption: string;
}
/** Unsaved fields stay in memory, isolated by account and announcement. */
@Injectable({ providedIn: 'root' })
export class EditorDrafts {
  private owner: string | null = null;
  private drafts = new Map<string, EditorDraft>();
  forOwner(owner: string | null) {
    if (owner !== this.owner) {
      this.drafts.clear();
      this.owner = owner;
    }
  }
  get(key: string) {
    return this.drafts.get(key);
  }
  set(key: string, value: EditorDraft) {
    this.drafts.set(key, value);
  }
  remove(key: string) {
    this.drafts.delete(key);
  }
}
