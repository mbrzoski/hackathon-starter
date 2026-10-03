// Mirrors the backend REST contracts (see /docs/API.md). Mock JSON in public/mock must match these shapes.

export interface Health {
  status: string;
  app: string;
  profiles: string[];
  time: string;
  database: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details: string[];
}
