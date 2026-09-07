import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static/app',
    emptyOutDir: true,
  },
  test: {
    environment: 'node',
  },
});
