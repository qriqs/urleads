import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig } from 'vite';

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      port: 3000,
      proxy: {
        '/api/v1/catalog': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/api/v1/quotes': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/api/v1/orders': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/api/v1/commercial-metrics': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/api/v1/metrics': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/api/v1/quote-templates': {
          target: process.env.VITE_COMERCIAL_URL || 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        '/ws': {
          target: process.env.VITE_WS_URL || 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
          secure: false,
        },
        '/api': {
          target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
        },
      },
    },
  };
});
