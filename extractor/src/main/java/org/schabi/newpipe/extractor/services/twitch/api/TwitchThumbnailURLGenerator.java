package org.schabi.newpipe.extractor.services.twitch.api;

public final class TwitchThumbnailURLGenerator {

    public static final String NO_THUMBNAIL_URL = "https://vod-secure.twitch" +
            ".tv/_404/404_processing_320x180.png";
    private static final String THUMBNAIL_URL_TEMPLATE = "https://static-cdn.jtvnw" +
            ".net/previews-ttv/live_user_%s-440x248.jpg";


    public static String getThumbnailURLForStream(final String streamerName) {
        return String.format(THUMBNAIL_URL_TEMPLATE, streamerName.toLowerCase());
    }
}
