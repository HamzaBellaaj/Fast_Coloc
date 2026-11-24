import {
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { combineLatest } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Auth } from '../../core/auth';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/feedback';
import { Listing } from '../listings/models';
import { BlockUser } from '../profile/block-user';
import { Conversation, Message, Messaging } from './messaging';
@Component({
  imports: [BlockUser, FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './messages.html',
  styleUrl: './messages.css',
})
export class Messages implements OnInit {
  auth = inject(Auth);
  service = inject(Messaging);
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroy = inject(DestroyRef);
  conversations = signal<Conversation[]>([]);
  selected = signal<Conversation | null>(null);
  listing = signal<Listing | null>(null);
  messages = signal<Message[]>([]);
  loading = signal(false);
  sending = signal(false);
  locallyBlocked = signal(false);
  blockChanged(blocked: boolean) {
    const wasBlocked = this.locallyBlocked();
    this.locallyBlocked.set(blocked);
    if (wasBlocked && !blocked && this.selected()) void this.refresh();
  }
  exchangeUnavailable() {
    return this.locallyBlocked() || this.selected()?.exchange_available === false;
  }
  error = signal('');
  inboxError = signal('');
  older = signal(false);
  moreInbox = signal(false);
  loadingOlder = signal(false);
  loadingInboxPage = signal(false);
  @ViewChild('history') historyElement?: ElementRef<HTMLDivElement>;
  private scrollToLatest(force = false) {
    const element = this.historyElement?.nativeElement;
    if (!element) return;
    const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 100;
    if (force || nearBottom)
      requestAnimationFrame(() => {
        element.scrollTop = element.scrollHeight;
      });
  }
  draft = '';
  private pending: { content: string; clientId: string } | null = null;
  private generation = 0;
  private page = 0;
  private polling = false;
  private lastRead = 0;
  get title() {
    return this.selected()?.listing_title ?? this.listing()?.title ?? '';
  }
  ngOnInit() {
    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(([params, query]) => {
        void this.open(params.get('id'), query.get('listing'));
      });
    const timer = setInterval(() => {
      if (!document.hidden && !this.polling && !this.loading() && !this.sending())
        void this.refresh();
    }, 5000);
    this.destroy.onDestroy(() => {
      clearInterval(timer);
      this.generation++;
    });
  }
  async open(id: string | null, listingId: string | null) {
    const g = ++this.generation;
    this.selected.set(null);
    this.locallyBlocked.set(false);
    this.listing.set(null);
    this.messages.set([]);
    this.draft = '';
    this.pending = null;
    this.lastRead = 0;
    this.older.set(false);
    this.error.set('');
    this.sending.set(false);
    this.loadingOlder.set(false);
    this.loadingInboxPage.set(false);
    this.loading.set(true);
    try {
      const inbox = await this.service.inbox();
      if (g !== this.generation) return;
      this.page = 0;
      this.conversations.set(inbox.items);
      this.moreInbox.set(inbox.total > inbox.items.length);
      this.inboxError.set('');
      if (id) {
        const conversation = await this.service.detail(id);
        if (g !== this.generation) return;
        this.selected.set(conversation);
        await this.loadHistory(g);
      } else if (listingId) {
        const existing =
          inbox.items.find((c) => c.listing_reference === listingId) ??
          (await this.service.lookup(listingId));
        if (g !== this.generation) return;
        if (existing) {
          void this.router.navigate(['/messages', existing.id], { replaceUrl: true });
          return;
        }
        const listing = await this.api.request<Listing>('GET', '/listings/' + listingId);
        if (g !== this.generation) return;
        if (listing.author_id === this.auth.profile()?.id)
          throw new Error('Tu ne peux pas te contacter toi-même.');
        this.listing.set(listing);
      }
    } catch (e) {
      if (g === this.generation) this.error.set(errorMessage(e));
    } finally {
      if (g === this.generation) this.loading.set(false);
    }
  }
  retry() {
    if (!this.selected() && !this.listing())
      void this.open(
        this.route.snapshot.paramMap.get('id'),
        this.route.snapshot.queryParamMap.get('listing'),
      );
    else void this.refresh();
  }
  async loadHistory(g = this.generation) {
    const c = this.selected();
    if (!c) return;
    const page = await this.service.history(c.id);
    if (g !== this.generation) return;
    const merged = new Map(this.messages().map((m) => [m.id, m]));
    page.items.forEach((m) => merged.set(m.id, m));
    const wasEmpty = this.messages().length === 0;
    this.scrollToLatest(wasEmpty);
    this.messages.set([...merged.values()].sort((a, b) => a.id - b.id));
    // Polling must not discard already loaded history or its pagination cursor.
    if (this.messages().length === page.items.length) this.older.set(page.hasMore);
    const newest = page.items.at(-1)?.id;
    if (newest && newest > this.lastRead && !document.hidden && document.hasFocus()) {
      await this.service.read(c.id, newest);
      if (g !== this.generation) return;
      this.lastRead = newest;
      this.conversations.update((rows) =>
        rows.map((row) => (row.id === c.id ? { ...row, unread_count: 0 } : row)),
      );
    }
  }
  async refresh() {
    if (this.polling) return;
    this.polling = true;
    const g = this.generation;
    try {
      const inbox = await this.service.inbox();
      if (g !== this.generation) return;
      if (this.page === 0) {
        this.conversations.set(inbox.items);
        this.moreInbox.set(inbox.total > inbox.items.length);
      } else
        this.conversations.update((rows) => [
          ...inbox.items,
          ...rows.filter((r) => !inbox.items.some((n) => n.id === r.id)),
        ]);
      this.inboxError.set('');
      const current = this.selected();
      if (current) {
        const latest =
          inbox.items.find((c) => c.id === current.id) ?? (await this.service.detail(current.id));
        if (g !== this.generation) return;
        this.selected.set(latest);
      }
      await this.loadHistory(g);
      if (g === this.generation) this.error.set('');
    } catch (e) {
      if (g === this.generation) this.inboxError.set(errorMessage(e));
    } finally {
      this.polling = false;
    }
  }
  async moreConversations() {
    if (this.loadingInboxPage()) return;
    this.loadingInboxPage.set(true);
    const g = this.generation;
    try {
      const page = await this.service.inbox(this.page + 1);
      if (g !== this.generation) return;
      this.page++;
      this.conversations.update((rows) => [
        ...rows,
        ...page.items.filter((n) => !rows.some((r) => r.id === n.id)),
      ]);
      this.moreInbox.set((this.page + 1) * page.size < page.total);
    } catch (e) {
      if (g === this.generation) this.inboxError.set(errorMessage(e));
    } finally {
      if (g === this.generation) this.loadingInboxPage.set(false);
    }
  }
  async olderMessages() {
    const c = this.selected(),
      before = this.messages()[0]?.id;
    if (!c || !before || this.loadingOlder()) return;
    const g = this.generation;
    this.loadingOlder.set(true);
    try {
      const page = await this.service.history(c.id, before);
      if (g !== this.generation) return;
      const merged = new Map([...page.items, ...this.messages()].map((m) => [m.id, m]));
      this.messages.set([...merged.values()].sort((a, b) => a.id - b.id));
      this.older.set(page.hasMore);
    } catch (e) {
      if (g === this.generation) this.error.set(errorMessage(e));
    } finally {
      if (g === this.generation) this.loadingOlder.set(false);
    }
  }
  async send() {
    const content = this.draft.trim(),
      c = this.selected(),
      listing = this.listing();
    if (this.exchangeUnavailable() || this.sending() || this.loading() || !content || content.length > 4000 || (!c && !listing))
      return;
    const g = this.generation;
    const request =
      this.pending?.content === content ? this.pending : { content, clientId: crypto.randomUUID() };
    this.pending = request;
    this.sending.set(true);
    this.error.set('');
    try {
      if (c) {
        const message = await this.service.send(c.id, content, request.clientId);
        if (g !== this.generation) return;
        this.messages.update((rows) =>
          [...rows.filter((m) => m.id !== message.id), message].sort((a, b) => a.id - b.id),
        );
        this.draft = '';
        this.pending = null;
        this.scrollToLatest(true);
        await this.refresh();
      } else {
        const conversation = await this.service.start(listing!.id, content, request.clientId);
        if (g !== this.generation) return;
        this.draft = '';
        this.pending = null;
        void this.router.navigate(['/messages', conversation.id], { replaceUrl: true });
      }
    } catch (e) {
      if (g === this.generation) this.error.set(errorMessage(e));
    } finally {
      if (g === this.generation) this.sending.set(false);
    }
  }
}
