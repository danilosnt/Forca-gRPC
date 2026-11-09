import { defineConfig } from 'vite';

// host: true deixa o front acessível pelos outros computadores da rede.
export default defineConfig({
  server: { port: 5173, strictPort: true, host: true },
  preview: { port: 5173, strictPort: true, host: true },
});
