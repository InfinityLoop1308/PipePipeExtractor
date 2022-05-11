package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import static org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper.clientId;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudStreamLinkHandlerFactory;

import java.io.IOException;
import java.util.List;

public class BilibiliCommentsLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(String url) throws ParsingException {
        return new BilibiliStreamLinkHandlerFactory().getId(url);
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, List<String> contentFilter, String sortFilter) throws ParsingException {
        id = id.startsWith("BV")? String.valueOf(new utils().bv2av(id)) :id;
        return "https://api.bilibili.com/x/v2/reply?type=1&sort=1&oid="+ id + "&pn=1";
    }
}
