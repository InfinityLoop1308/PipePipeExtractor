package org.schabi.newpipe.extractor.services.niconico.extractors;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.*;
import static org.schabi.newpipe.extractor.services.bilibili.utils.getDurationFromString;

public class NiconicoSeriesContentItemExtractor implements StreamInfoItemExtractor {
    private final String uploaderUrl;
    private final Element data;
    private final String uploaderName;

    public NiconicoSeriesContentItemExtractor(Element element, String url, String uploaderName) {
        this.uploaderUrl = url;
        this.data = element;
        this.uploaderName = uploaderName;
    }

    @Override
    public String getName() throws ParsingException {
        return data.select("div.NC-Thumbnail-image").attr("aria-label");
    }

    @Override
    public String getUrl() throws ParsingException {
        return data.select("a.NC-Link.NC-MediaObject-contents").attr("href");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.select("div.NC-Thumbnail-image").attr("data-background-image");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() throws ParsingException {
        return getDurationFromString(data.select("div.NC-VideoLength").text());
    }

    @Override
    public long getViewCount() throws ParsingException {
        long value = 0;
        Pattern pattern = Pattern.compile("(\\d+)([KM]?)");
        Matcher matcher = pattern.matcher(data.select("div.NC-VideoMetaCount_view").text().replaceAll(",", ""));
        if (matcher.matches()) {
            value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            if (unit.equals("K")) {
                value = value * 1000;
            } else if (unit.equals("M")) {
                value = value * 1000 * 1000;
            }
        }
        return value;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return uploaderName;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return uploaderUrl;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return data.select("span.NC-VideoRegisteredAtText-text").text();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4)
                .appendLiteral("/")
                .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL)
                .appendLiteral("/")
                .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NORMAL)
                .appendLiteral(" ")
                .appendValue(HOUR_OF_DAY, 1, 2, SignStyle.NORMAL)
                .appendLiteral(":")
                .appendValue(MINUTE_OF_HOUR, 1, 2, SignStyle.NORMAL)
                .toFormatter();

        try {
            return new DateWrapper(LocalDateTime.parse(
                    Objects.requireNonNull(getTextualUploadDate()), formatter).atOffset(ZoneOffset.ofHours(9)));
        } catch (Exception e) {
            return null;
        }
    }
}
