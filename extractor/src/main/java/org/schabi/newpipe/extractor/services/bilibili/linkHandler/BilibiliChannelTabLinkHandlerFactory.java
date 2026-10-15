package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.util.List;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

/**
 * Builds channel tab handlers. Only the videos tab can be built from a query (with an optional
 * sort), other tabs are provided by {@code BilibiliChannelExtractor#getTabs()}. Throwing for
 * unsupported tabs lets the client know e.g. that in-channel search is unavailable.
 */
public class BilibiliChannelTabLinkHandlerFactory extends BilibiliChannelLinkHandlerFactory {

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        if (contentFilter != null && !contentFilter.isEmpty()
                && !ChannelTabs.VIDEOS.equals(contentFilter.get(0).getName())) {
            throw new ParsingException("Unsupported Bilibili channel tab: "
                    + contentFilter.get(0).getName());
        }
        return super.getUrl(id, contentFilter, sortFilter);
    }
}
