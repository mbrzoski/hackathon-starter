import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// Each front (/listen, /senior, /family) renders its own header.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class AppComponent {}
