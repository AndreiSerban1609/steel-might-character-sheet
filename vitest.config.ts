import { defineConfig } from 'vitest/config';

// Frontend unit tests (Story 6.1). Store + domain tests run in node with the platform
// seams (HTTP, OBR metadata) mocked per file; component smoke tests under
// src/presentation run in jsdom with @testing-library/react.
export default defineConfig({
  test: {
    environment: 'node',
    environmentMatchGlobs: [['src/presentation/**', 'jsdom']],
    include: ['src/**/*.test.{ts,tsx}'],
    clearMocks: true,
  },
});
