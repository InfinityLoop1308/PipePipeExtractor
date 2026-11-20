package org.schabi.newpipe.extractor.services.niconico;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.AntiBotException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class NiconicoServiceParsingHelper {
    private NiconicoServiceParsingHelper() {

    }

    /**
     * @throws AntiBotException if the CloudFront firewall in front of NicoNico rejected the
     *                          request, which it answers with HTTP 403 and a "Request blocked"
     *                          page instead of NicoNico's own error pages
     */
    public static void throwIfBlocked(final Response response) throws AntiBotException {
        final String body = response.responseBody();
        if (response.responseCode() == 403 && body != null
                && body.contains("Request blocked")
                && body.contains("CloudFront")) {
            throw new AntiBotException(ServiceList.NicoNico.getServiceId(),
                    "NicoNico's firewall blocked the request", response.latestUrl(), null);
        }
    }

    public static DateWrapper parseSnapshotDateTime(final String textDateTime) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.parse(
                textDateTime, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        return new DateWrapper(zonedDateTime.toOffsetDateTime());
    }

    public static DateWrapper parseRSSDateTime(final String textDateTime) {
        final LocalDateTime localDateTime = LocalDateTime.parse(
                textDateTime, DateTimeFormatter.ofPattern("uuuu'年'MM'月'dd'日' HH'：'mm'：'ss"));
        // given datetime is
        return new DateWrapper(localDateTime.atOffset(ZoneOffset.ofHours(9)));
    }
}
