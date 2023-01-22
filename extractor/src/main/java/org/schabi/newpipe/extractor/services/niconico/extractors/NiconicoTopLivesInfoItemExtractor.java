package org.schabi.newpipe.extractor.services.niconico.extractors;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class NiconicoTopLivesInfoItemExtractor implements StreamInfoItemExtractor {
    Element data;

    public NiconicoTopLivesInfoItemExtractor(Element e) {
        data = e;
    }

    @Override
    public String getName() throws ParsingException {
        return data.select("a[class^=___rk-program-card-detail-title] > span").text();
    }

    @Override
    public String getUrl() throws ParsingException {
        return data.select("a[class^=___rk-program-card-detail-title]").attr("href");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.select("a[class^=___rk-program-card-thumbnail] > img").attr("src");
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        return StreamType.LIVE_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Override
    public long getDuration() throws ParsingException {
        return -1;
    }

    @Override
    public long getViewCount() throws ParsingException {
        return Long.parseLong(data.select("li[class^=___rk-program-card-detail-counts-view-count] > span")
                .attr("data-value"));
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.select("a[class^=___rk-program-card-detail-provider-name] > span").text();
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
        return data.select("div[class^=___rk-program-card-detail-time] > span").text();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
//        try{
//            String textualUploadDate = getTextualUploadDate();
//            String hour = textualUploadDate.split("時間")[0];
//            String minutes = textualUploadDate.split("分")[0];
//            if(textualUploadDate.contains("時間")){
//                minutes = minutes.split("時間")[1];
//            }
//        } catch (Exception e){
//            return null;
//        }
//        return new DateWrapper(LocalDateTime.parse(
//                getTextualUploadDate().split(Pattern.quote("+"))[0].replace("T"," "), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(9)));
    }
}
