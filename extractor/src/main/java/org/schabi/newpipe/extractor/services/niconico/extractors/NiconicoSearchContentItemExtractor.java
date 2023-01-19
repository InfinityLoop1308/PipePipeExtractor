package org.schabi.newpipe.extractor.services.niconico.extractors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class NiconicoSearchContentItemExtractor implements StreamInfoItemExtractor {
    private final Element data;

    public NiconicoSearchContentItemExtractor(Element e) {
        this.data = e;
    }

    @Override
    public String getName() throws ParsingException {
        return data.select("p.itemTitle > a").text();
    }

    @Override
    public String getUrl() throws ParsingException {
        return NiconicoService.BASE_URL + data.select("p.itemTitle > a").attr("href");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        String result = null;
        try {
            result = data.select(".jsLazyImage").attr("data-original");
        } catch (Exception ignored){
        }
        return StringUtils.defaultIfBlank(result, data.select("img.thumb").attr("src"));
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        String duration = data.select("span.videoLength").text();
        long result = 0;
        int len = duration.split(":").length;
        try {
            result += Integer.parseInt(duration.split(":")[len-1]);
            result += Integer.parseInt(duration.split(":")[len-2]) * 60;
            result += Integer.parseInt(duration.split(":")[len-3]) * 3600;
        } catch (Exception e){
            //e.printStackTrace();
        }
        return  result;
    }

    @Override
    public long getViewCount() throws ParsingException {
        try {
            return (long) NumberFormat.getNumberInstance(java.util.Locale.US).parse(data.select(".count.view > span").text());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return data.select("span.time").text();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(LocalDateTime.parse(
                getTextualUploadDate(), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")).atOffset(ZoneOffset.ofHours(9)));
    }
}
