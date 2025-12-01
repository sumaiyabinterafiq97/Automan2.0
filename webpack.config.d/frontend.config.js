const path = require('path');
// Use absolute path - this file is in webpack.config.d/ which is in project root
// So __dirname here is the project root
const contentBase = path.resolve(__dirname, 'build/dist/js/developmentExecutable');

config.devServer = {
    ...config.devServer,
    port: 8081,
    host: 'localhost',
    static: {
        directory: contentBase,
        publicPath: '/',
        watch: true,
    },
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
                target: ' http://localhost:8083',
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
