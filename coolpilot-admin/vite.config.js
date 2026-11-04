import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {fileURLToPath, URL} from 'node:url'

// 部署在 /cms/ 下，与旧版一致，后端 CORS 与 nginx 配置无需改动
export default defineConfig({
	base: '/cms/',
	plugins: [vue()],
	resolve: {
		alias: {
			'@': fileURLToPath(new URL('./src', import.meta.url))
		}
	},
	server: {
		host: '0.0.0.0',
		port: 8079,
		proxy: {
			// 与旧版一致：/api/** → 后端 /**（去掉 /api 前缀）
			'/api': {
				target: process.env.API_TARGET || 'http://localhost:8091',
				changeOrigin: true,
				rewrite: path => path.replace(/^\/api/, '')
			}
		}
	},
	build: {
		outDir: 'dist',
		chunkSizeWarningLimit: 1500
	}
})
