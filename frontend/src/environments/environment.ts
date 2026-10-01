// Production defaults (used by `ng build` and the Docker image). nginx proxies /api to the backend.
export const environment = {
  production: true,
  apiUrl: '/api',
  useMocks: false,
};
