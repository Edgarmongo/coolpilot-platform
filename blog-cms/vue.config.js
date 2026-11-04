'use strict'
const path = require('path')
const defaultSettings = require('./src/settings.js')

function resolve(dir) {
	return path.join(__dirname, dir)
}

const name = defaultSettings.title // page title

// If your port is set to 80,
// use administrator privileges to execute the command line.
// For example, Mac: sudo npm run
// You can change the port by the following methods:
// port = 9528 npm run dev OR npm run dev --port = 9528
const port = process.env.port || process.env.npm_config_port || 8079 // dev port

// All configuration item explanations can be find in https://cli.vuejs.org/config/
module.exports = {
	/**
	 * You will need to set publicPath if you plan to deploy your site under a sub path,
	 * for example GitHub Pages. If you plan to deploy your site to https://foo.github.io/bar/,
	 * then publicPath should be set to "/bar/".
	 * In most cases please use '/' !!!
	 * Detail: https://cli.vuejs.org/config/#publicpath
	 */
	publicPath: '/cms/',
	outputDir: 'dist',
	assetsDir: 'static',
	lintOnSave: process.env.NODE_ENV === 'development',
	productionSourceMap: false,
	devServer: {
		port: port,
		//Docker 内没有桌面环境，启动时不要尝试打开浏览器
		open: false,
		overlay: {
			warnings: false,
			errors: true
		},
		proxy: {
			//开发环境将 /api/ 请求代理到本地后端服务
			//request.js 中 baseURL 为 /api/admin/，改写后即 /admin/**
			'/api': {
				//Docker 内通过 API_TARGET=http://api:8091 指向后端容器；本机开发沿用 localhost
				target: process.env.API_TARGET || 'http://localhost:8091',
				changeOrigin: true,
				pathRewrite: {
					'^/api': ''
				}
			}
		},
	},
	configureWebpack: {
		// provide the app's title in webpack's name field, so that
		// it can be accessed in index.html to inject the correct title.
		name: name,
		resolve: {
			alias: {
				'@': resolve('src')
			}
		}
	},
	chainWebpack(config) {
		// it can improve the speed of the first screen, it is recommended to turn on preload
		config.plugin('preload').tap(() => [
			{
				rel: 'preload',
				// to ignore runtime.js
				// https://github.com/vuejs/vue-cli/blob/dev/packages/@vue/cli-service/lib/config/app.js#L171
				fileBlacklist: [/\.map$/, /hot-update\.js$/, /runtime\..*\.js$/],
				include: 'initial'
			}
		])

		// when there are many pages, it will cause too many meaningless requests
		config.plugins.delete('prefetch')

		// set svg-sprite-loader
		config.module
		.rule('svg')
		.exclude.add(resolve('src/icons'))
		.end()
		config.module
		.rule('icons')
		.test(/\.svg$/)
		.include.add(resolve('src/icons'))
		.end()
		.use('svg-sprite-loader')
		.loader('svg-sprite-loader')
		.options({
			symbolId: 'icon-[name]'
		})
		.end()
	}
}
