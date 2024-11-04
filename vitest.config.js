import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.js'],
    environment: 'node',
    globals: true,
    restoreMocks: true,
    clearMocks: true,
    reporters: ['default'],
    testTimeout: 10000,
  },
});
