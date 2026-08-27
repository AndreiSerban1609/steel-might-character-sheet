import { defineConfig } from 'vitest/config';

// Frontend unit tests (Story 6.1): store + domain logic. No DOM — the store is exercised
// directly through zustand's getState/setState with the platform seams (HTTP, OBR
// metadata) mocked per test file.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    clearMocks: true,
  },
});
