const path = require('path');
// When this config is included in the generated webpack.config.js,
// __dirname points to build/js/packages/automan-car-purchase
// So we need to go up 4 levels to get to project root: automan-car-purchase -> packages -> js -> build -> project root
const projectRoot = path.resolve(__dirname, '../../../..');
const contentBase = path.resolve(projectRoot, 'build/dist/js/developmentExecutable');

config.devServer = {
    ...config.devServer,
    port: 8081,
    host: 'localhost',
    static: [
        {
            directory: contentBase,
            publicPath: '/',
            watch: true,
        },
        // Keep Kotlin/JS generated files accessible
        path.resolve(__dirname, 'build/js/packages/automan-car-purchase/kotlin'),
        path.resolve(__dirname, 'build/processedResources/js/main')
    ],
    historyApiFallback: {
        index: 'index.html',
        disableDotRule: true,
    },
    open: false,
    hot: true,
    compress: true,
    allowedHosts: 'all',
        proxy: {
            '/api': {
                target: 'http://localhost:8083',
                changeOrigin: true,
                secure: false
            },
            '/purchases': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/purchases': '/api/purchases' },
                changeOrigin: true,
                secure: false
            },
            '/rixo/prices': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/rixo': '/api/rixo' },
                changeOrigin: true,
                secure: false
            },
            '/rixo/mappings': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/rixo': '/api/rixo' },
                changeOrigin: true,
                secure: false
            },
            '/rixo/dropdowns': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/rixo': '/api/rixo' },
                changeOrigin: true,
                secure: false
            },
            '/rixo/import': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/rixo': '/api/rixo' },
                changeOrigin: true,
                secure: false
            },
            '/auth': {
                target: 'http://localhost:8083',
                pathRewrite: { '^/auth': '/api/auth' },
                changeOrigin: true,
                secure: false
            }
        }
};
