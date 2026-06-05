import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'com.monochrome.app',
    appName: 'Fabiodalez Music',
    webDir: 'dist',
    // Use monochrome.tf as the WebView origin so that Tidal API proxies
    // and CDN accept our requests (they reject Origin: https://localhost).
    server: {
        hostname: 'monochrome.tf',
        androidScheme: 'https',
    },
    android: {
        adjustMarginsForEdgeToEdge: 'auto',
        allowMixedContent: false,
    },
    plugins: {
        // Use the official @capacitor/status-bar plugin key. The previous
        // SystemBars key was ignored by the installed plugin, allowing the
        // WebView/header to draw underneath the Android status bar on some ROMs.
        StatusBar: {
            style: 'DARK',
            backgroundColor: '#000000',
            overlaysWebView: false,
        },
        // #39: SplashScreen config — no more white flash on launch
        SplashScreen: {
            launchShowDuration: 1500,
            launchAutoHide: true,
            launchFadeOutDuration: 300,
            backgroundColor: '#000000',
            androidScaleType: 'CENTER_CROP',
            showSpinner: false,
            splashFullScreen: true,
            splashImmersive: false,
        },
    },
};

export default config;
