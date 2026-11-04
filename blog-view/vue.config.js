module.exports = {
	devServer: {
		port: 8081,
		proxy: {
			//开发环境将 /api/ 请求代理到本地后端服务
			'/api': {
				//Docker 内通过 API_TARGET=http://api:8091 指向后端容器；本机开发沿用 localhost
				target: process.env.API_TARGET || 'http://localhost:8091',
				changeOrigin: true,
				pathRewrite: {
					'^/api': ''
				}
			}
		}
	},
	configureWebpack: {
		resolve: {
			alias: {
				'assets': '@/assets',
				'common': '@/common',
				'components': '@/components',
				'api': '@/api',
				'views': '@/views',
				'plugins': '@/plugins'
			}
		},
		optimization: {
			splitChunks: {
				cacheGroups: {
					vendor: {
						test: /[\\/]node_modules[\\/]/,
						name(module) {
							// get the name. E.g. node_modules/packageName/not/this/part.js
							// or node_modules/packageName
							const packageName = module.context.match(
								/[\\/]node_modules[\\/](.*?)([\\/]|$)/
							)[1];
							// npm package names are URL-safe, but some servers don't like @ symbols
							return `npm.${packageName.replace("@", "")}`;
						},
						chunks: "all",
						enforce: true,
						priority: 10,
						minSize: 50000, // 50KB
						maxSize: 200000,
						reuseExistingChunk: true,
					},
				},
			},
		},
	},
}