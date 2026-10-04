import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { notOnStart } from './app-target.family';

@Component({ template: '' })
class Page {}

describe('family app start page', () => {
  async function start(url: string) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'family', component: Page },
          { path: 'setup', component: Page, canActivate: [notOnStart] },
        ]),
      ],
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl(url);
    return router;
  }

  it('opens /setup as the first page on the panel instead', async () => {
    expect((await start('/setup')).url).toBe('/family');
  });

  it('opens /setup normally from the panel', async () => {
    const router = await start('/family');
    await router.navigateByUrl('/setup');
    expect(router.url).toBe('/setup');
  });
});
