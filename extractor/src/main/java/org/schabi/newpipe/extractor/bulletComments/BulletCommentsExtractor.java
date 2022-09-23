package org.schabi.newpipe.extractor.bulletComments;

import javax.annotation.Nonnull;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

public abstract class BulletCommentsExtractor extends ListExtractor<BulletCommentsInfoItem> {
    public BulletCommentsExtractor(final StreamingService service, final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "BulletComments";
    }
}
