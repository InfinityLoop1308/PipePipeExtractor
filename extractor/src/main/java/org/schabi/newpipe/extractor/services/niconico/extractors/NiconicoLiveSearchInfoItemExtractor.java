package org.schabi.newpipe.extractor.services.niconico.extractors;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

public class NiconicoLiveSearchInfoItemExtractor implements StreamInfoItemExtractor {
    Element data;
    public NiconicoLiveSearchInfoItemExtractor(Element e) {
        data = e;
    }

    @Override
    public String getName() throws ParsingException {
        return data.select("a.searchPage-ProgramList_TitleLink").text();
    }

    @Override
    public String getUrl() throws ParsingException {
        return "https://live.nicovideo.jp/" + data.select("a.searchPage-ProgramList_TitleLink").attr("href");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return data.select("img.searchPage-ProgramList_Image").attr("src").replace("http:", "https");
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
        return Long.parseLong(data.select("span.searchPage-ProgramList_DataText").get(1).text());
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return data.select("p.searchPage-ProgramList_UserName > a.searchPage-ProgramList_UserNameLink").text();
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return data.select("img.searchPage-ProgramList_UserImage").attr("src");
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        return data.select("li.searchPage-ProgramList_DataItem").first().select("span").text();
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return data.select("p.searchPage-ProgramList_Description").text();
    }
}
