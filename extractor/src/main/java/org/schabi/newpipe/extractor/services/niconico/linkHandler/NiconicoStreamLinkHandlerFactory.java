package org.schabi.newpipe.extractor.services.niconico.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.utils.Parser;

import edu.umd.cs.findbugs.annotations.NonNull;

public class NiconicoStreamLinkHandlerFactory extends LinkHandlerFactory {
    @NonNull
    @Override
    public String getId(final String url) throws ParsingException {
        return Parser.matchGroup(NiconicoService.SMILEVIDEO, url, 2);
    }

    @Override
    public String getUrl(final String id) throws ParsingException {
        return NiconicoService.SP_WATCH_URL + id;
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            getId(url);
            return true;
        } catch (final ParsingException e) {
            throw new ParsingException(e.getMessage());
        }
    }
}
