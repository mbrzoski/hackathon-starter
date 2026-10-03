import { Component, input } from '@angular/core';

export type IconName =
  | 'shield' | 'phone' | 'mic' | 'warning' | 'check' | 'mail' | 'image' | 'lock' | 'monitor' | 'send' | 'help' | 'pause' | 'play' | 'mic-off' | 'cloud-off';

/** Inline SVG icons (stroke, currentColor). Decorative: meaning is always carried by adjacent text. */
@Component({
  selector: 'app-icon',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @switch (name()) {
        @case ('shield') { <path d="M12 3l8 3v6c0 4.5-3.2 8.3-8 9-4.8-.7-8-4.5-8-9V6z" /><path d="M8.5 12l2.5 2.5 4.5-5" /> }
        @case ('phone') { <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" /> }
        @case ('mic') { <rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8" /> }
        @case ('warning') { <path d="M12 3l10 17H2z" /><path d="M12 10v4M12 17v.5" /> }
        @case ('check') { <path d="M5 12.5l4.5 4.5L19 7.5" /> }
        @case ('mail') { <rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 7l9 6 9-6" /> }
        @case ('image') { <rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="1.8" /><path d="M4 18l5-5 4 4 3-3 4 4" /> }
        @case ('lock') { <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /> }
        @case ('monitor') { <rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 21h8M12 16v5" /> }
        @case ('send') { <path d="M21 3L3 11l7 3 3 7z" /><path d="M10 14l11-11" /> }
        @case ('pause') { <rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /> }
        @case ('play') { <path d="M7 4l13 8-13 8z" /> }
        @case ('mic-off') { <path d="M3 3l18 18" /><path d="M9 9v2a3 3 0 0 0 5 2.2M15 9.3V6a3 3 0 0 0-5.7-1.3" /><path d="M5 11a7 7 0 0 0 11.5 5.4M19 11a7 7 0 0 1-.7 3M12 18v3M8 21h8" /> }
        @case ('cloud-off') { <path d="M3 3l18 18" /><path d="M7 18h10.5M20.5 15.5A4 4 0 0 0 17 10h-1A7 7 0 0 0 9 5.3M5.5 8A5 5 0 0 0 7 18" /> }
        @case ('help') { <circle cx="12" cy="12" r="9" /><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17v.5" /> }
      }
    </svg>
  `,
  styles: `:host { display: inline-flex; line-height: 0; }`,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(24);
}
