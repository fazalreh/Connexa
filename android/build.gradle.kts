plugins {
    id("com.android.application") version "9.3.1" apply false
    // Reads google-services.json at build time. The file is gitignored, so the plugin is
    // applied conditionally in :app and a checkout without it still builds.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
