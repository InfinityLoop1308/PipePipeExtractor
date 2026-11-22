package org.schabi.newpipe.extractor.comments;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public abstract class CommentsExtractor extends ListExtractor<CommentsInfoItem> {

    public CommentsExtractor(final StreamingService service, final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    /**
     * @apiNote Warning: This method is experimental and may get removed in a future release.
     * @return <code>true</code> if the comments are disabled otherwise <code>false</code> (default)
     */
    public boolean isCommentsDisabled() throws ExtractionException {
        return false;
    }

    /** Available comment sort orders, in the order advertised by the service. */
    public List<String> getSortOrders() {
        return Collections.emptyList();
    }

    /** The selected sort order, or -1 when the service does not expose sorting. */
    public int getSortOrder() {
        return -1;
    }

    /** Select a sort order before fetching; -1 uses the service default. */
    public void setSortOrder(final int sortOrder) {
        if (sortOrder != -1) {
            throw new IllegalArgumentException("Comment sorting is not supported");
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "Comments";
    }
}
