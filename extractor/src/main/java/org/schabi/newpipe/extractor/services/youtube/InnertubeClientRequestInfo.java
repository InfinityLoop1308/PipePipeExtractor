package org.schabi.newpipe.extractor.services.youtube;

import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.ANDROID_CLIENT_ID;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.ANDROID_CLIENT_NAME;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.ANDROID_CLIENT_VERSION;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.MOBILE_CLIENT_PLATFORM;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.VISIONOS_CLIENT_ID;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.VISIONOS_CLIENT_NAME;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.VISIONOS_CLIENT_VERSION;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.VISIONOS_DEVICE_MODEL;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.VISIONOS_VERSION;
import static org.schabi.newpipe.extractor.services.youtube.ClientsConstants.WATCH_CLIENT_SCREEN;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO: add docs

public final class InnertubeClientRequestInfo {

    @Nonnull
    public ClientInfo clientInfo;
    @Nonnull
    public DeviceInfo deviceInfo;

    public static final class ClientInfo {

        @Nonnull
        public String clientName;
        @Nonnull
        public String clientVersion;
        @Nonnull
        public String clientScreen;
        @Nullable
        public String clientId;
        @Nullable
        public String visitorData;

        private ClientInfo(@Nonnull final String clientName,
                           @Nonnull final String clientVersion,
                           @Nonnull final String clientScreen,
                           @Nullable final String clientId,
                           @Nullable final String visitorData) {
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.clientScreen = clientScreen;
            this.clientId = clientId;
            this.visitorData = visitorData;
        }
    }

    public static final class DeviceInfo {

        @Nonnull
        public String platform;
        @Nullable
        public String deviceMake;
        @Nullable
        public String deviceModel;
        @Nullable
        public String osName;
        @Nullable
        public String osVersion;
        public int androidSdkVersion;

        private DeviceInfo(@Nonnull final String platform,
                           @Nullable final String deviceMake,
                           @Nullable final String deviceModel,
                           @Nullable final String osName,
                           @Nullable final String osVersion,
                           final int androidSdkVersion) {
            this.platform = platform;
            this.deviceMake = deviceMake;
            this.deviceModel = deviceModel;
            this.osName = osName;
            this.osVersion = osVersion;
            this.androidSdkVersion = androidSdkVersion;
        }
    }

    private InnertubeClientRequestInfo(@Nonnull final ClientInfo clientInfo,
                                       @Nonnull final DeviceInfo deviceInfo) {
        this.clientInfo = clientInfo;
        this.deviceInfo = deviceInfo;
    }

    @Nonnull
    public static InnertubeClientRequestInfo ofAndroidClient() {
        return new InnertubeClientRequestInfo(
                new InnertubeClientRequestInfo.ClientInfo(ANDROID_CLIENT_NAME,
                        ANDROID_CLIENT_VERSION, WATCH_CLIENT_SCREEN, ANDROID_CLIENT_ID, null),
                new InnertubeClientRequestInfo.DeviceInfo(MOBILE_CLIENT_PLATFORM, null, null,
                        "Android", "16", 36));
    }

    @Nonnull
    public static InnertubeClientRequestInfo ofVisionOsClient() {
        return new InnertubeClientRequestInfo(
                new InnertubeClientRequestInfo.ClientInfo(VISIONOS_CLIENT_NAME,
                        VISIONOS_CLIENT_VERSION, WATCH_CLIENT_SCREEN, VISIONOS_CLIENT_ID, null),
                new InnertubeClientRequestInfo.DeviceInfo(MOBILE_CLIENT_PLATFORM, "Apple",
                        VISIONOS_DEVICE_MODEL, "visionOS", VISIONOS_VERSION, -1));
    }

}
