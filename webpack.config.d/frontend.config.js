config.devServer = {
    ...config.devServer,
    port: 8081,
    host: 'localhost',
        proxy: {
            '/api': {
                target: 'http://localhost:8083',
                changeOrigin: true,
                secure: false
            }
        }
};
