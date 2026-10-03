import { Component, inject } from '@angular/core';
import { EventsService } from '../../core/events.service';
import { Icon } from '../../shared/icon';
import { ModeBadge } from '../../shared/mode-badge';
import { AlertCard } from './alert-card';
import { FamilyFeed } from './family-feed';
import { SystemStatusBar } from '../../shared/system-status-bar';

/** Family panel: mobile first from 360 px (WEB-12). Alerts newest first; levels and texts come from the backend. */
@Component({
  selector: 'app-family',
  imports: [AlertCard, Icon, ModeBadge, SystemStatusBar],
  templateUrl: './family.html',
  styleUrl: './family.scss',
})
export class Family {
  protected readonly feed = inject(FamilyFeed);
  private readonly events = inject(EventsService);

  /** The live transcript belongs only to the ongoing call's alerts. */
  protected segmentsFor(callId: string) {
    return this.events.activeCall()?.callId === callId ? this.events.segments() : [];
  }
}
