package org.schabi.newpipe.extractor.services.youtube;

final class ClientsConstants {
    private ClientsConstants() {
    }

    // Common client fields

    static final String DESKTOP_CLIENT_PLATFORM = "DESKTOP";
    static final String MOBILE_CLIENT_PLATFORM = "MOBILE";
    static final String WATCH_CLIENT_SCREEN = "WATCH";

    // WEB (YouTube desktop) client fields

    static final String WEB_CLIENT_ID = "1";
    static final String WEB_CLIENT_NAME = "WEB";
    /**
     * The client version for InnerTube requests with the {@code WEB} client, used as the last
     * fallback if the extraction of the real one failed.
     */
    static final String WEB_HARDCODED_CLIENT_VERSION = "2.20250122.04.00";

    // WEB_REMIX (YouTube Music) client fields

    static final String WEB_REMIX_CLIENT_ID = "67";
    static final String WEB_REMIX_CLIENT_NAME = "WEB_REMIX";
    static final String WEB_REMIX_HARDCODED_CLIENT_VERSION = "1.20250122.01.00";

    // ANDROID (Android YouTube app) client fields

    static final String ANDROID_CLIENT_ID = "3";
    static final String ANDROID_CLIENT_NAME = "ANDROID";

    /**
     * The hardcoded client version of the Android app used for InnerTube requests with this
     * client.
     *
     * <p>
     * It can be extracted by getting the latest release version of the app in an APK repository
     * such as <a href="https://www.apkmirror.com/apk/google-inc/youtube/">APKMirror</a>.
     * </p>
     */
    static final String ANDROID_CLIENT_VERSION = "21.03.36";

    // VISIONOS (Apple Vision Pro) client fields

    static final String VISIONOS_CLIENT_ID = "101";
    static final String VISIONOS_CLIENT_NAME = "VISIONOS";
    static final String VISIONOS_CLIENT_VERSION = "1.02";
    static final String VISIONOS_DEVICE_MODEL = "RealityDevice14,1";
    static final String VISIONOS_VERSION = "25.6.0.23O471";
    static final String VISIONOS_USER_AGENT_VERSION = "25_6_0";

}
