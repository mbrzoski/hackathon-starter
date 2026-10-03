import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the health endpoint under the configured API url', () => {
    api.health().subscribe((h) => expect(h.status).toBe('UP'));
    const req = http.expectOne('/api/health');
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'UP' });
  });

  it('GETs the settings endpoint', () => {
    api.settings().subscribe((s) => expect(s.emergencyNumber).toBe('112'));
    const req = http.expectOne('/api/settings');
    expect(req.request.method).toBe('GET');
    req.flush({ emergencyNumber: '112' });
  });
});
