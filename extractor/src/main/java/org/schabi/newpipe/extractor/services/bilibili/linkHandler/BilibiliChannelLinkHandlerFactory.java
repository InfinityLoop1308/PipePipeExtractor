package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;

public class BilibiliChannelLinkHandlerFactory extends ListLinkHandlerFactory{
    
    public static final String baseUrl = "https://space.bilibili.com/";

    @Override
    public String getId(final String url) throws ParsingException {
        if (url.contains("mid=")) {
            return url.split("mid=")[1];
        } else {
            throw new ParsingException("Not a bilibili channel link.");
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, List<String> contentFilter, String sortFilter) throws ParsingException {
        return "https://api.bilibili.com/x/space/arc/search?pn=1&ps=10&mid=" + id;
    }

}
 