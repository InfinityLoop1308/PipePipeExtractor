package org.schabi.newpipe.extractor.comments;

import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.utils.ExtractorHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CommentsInfo extends ListInfo<CommentsInfoItem> {

    private CommentsInfo(
            final int serviceId,
            final ListLinkHandler listUrlIdHandler,
            final String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static CommentsInfo getInfo(final String url) throws IOException, ExtractionException {
        return getInfo(NewPipe.getServiceByUrl(url), url);
    }

    public static CommentsInfo getInfo(final StreamingService service, final String url)
            throws ExtractionException, IOException {
        return getInfo(service.getCommentsExtractor(url));
    }

    public static CommentsInfo getInfo(final CommentsExtractor commentsExtractor)
            throws IOException, ExtractionException {
        // for services which do not have a comments extractor
        if (commentsExtractor == null) {
            return null;
        }

        commentsExtractor.fetchPage();

        final String name = commentsExtractor.getName();
        final int serviceId = commentsExtractor.getServiceId();
        final ListLinkHandler listUrlIdHandler = commentsExtractor.getLinkHandler();

        final CommentsInfo commentsInfo = new CommentsInfo(serviceId, listUrlIdHandler, name);
        commentsInfo.setCommentsExtractor(commentsExtractor);
        final InfoItemsPage<CommentsInfoItem> initialCommentsPage = applyCommentsFilter(
                commentsExtractor.getService(), commentsExtractor,
                ExtractorHelper.getItemsPageOrLogError(commentsInfo, commentsExtractor));
        commentsInfo.setCommentsDisabled(commentsExtractor.isCommentsDisabled());
        commentsInfo.setRelatedItems(initialCommentsPage.getItems());
        commentsInfo.setNextPage(initialCommentsPage.getNextPage());

        return commentsInfo;
    }

    public static CommentsInfo getInfoTemplate(final CommentsExtractor commentsExtractor) throws ParsingException {
        final String name = commentsExtractor.getName();
        final int serviceId = commentsExtractor.getServiceId();
        final ListLinkHandler listUrlIdHandler = commentsExtractor.getLinkHandler();

        return new CommentsInfo(serviceId, listUrlIdHandler, name);
    }

    public static InfoItemsPage<CommentsInfoItem> getMoreItems(
            final CommentsInfo commentsInfo,
            final Page page) throws ExtractionException, IOException {
        return getMoreItems(NewPipe.getService(commentsInfo.getServiceId()), commentsInfo.getUrl(),
                page);
    }

    public static InfoItemsPage<CommentsInfoItem> getMoreItems(
            final StreamingService service,
            final CommentsInfo commentsInfo,
            final Page page) throws IOException, ExtractionException {
        return getMoreItems(service, commentsInfo.getUrl(), page);
    }

    public static InfoItemsPage<CommentsInfoItem> getMoreItems(
            final StreamingService service,
            final String url,
            final Page page) throws IOException, ExtractionException {
        final CommentsExtractor extractor = service.getCommentsExtractor(url);
        return applyCommentsFilter(service, extractor, extractor.getPage(page));
    }

    /**
     * Maximum number of extra pages fetched when every comment of a page gets filtered out.
     */
    private static final int MAX_FILTERED_EMPTY_PAGE_FETCHES = 10;

    /**
     * Removes comments matching the user's comment filters. If a whole page is filtered out,
     * following pages are fetched (up to a limit) so that pagination does not stop early.
     */
    private static InfoItemsPage<CommentsInfoItem> applyCommentsFilter(
            final StreamingService service,
            final CommentsExtractor extractor,
            final InfoItemsPage<CommentsInfoItem> page) {
        final CommentsFilterConfig config = service.getCommentsFilterConfig();
        if (config == null || config.isEmpty() || page == null) {
            return page;
        }

        final List<Throwable> errors = new ArrayList<>(page.getErrors());
        List<CommentsInfoItem> items = config.filter(page.getItems());
        Page nextPage = page.getNextPage();
        int extraFetches = 0;
        while (items.isEmpty() && Page.isValid(nextPage)
                && extraFetches < MAX_FILTERED_EMPTY_PAGE_FETCHES) {
            extraFetches++;
            try {
                final InfoItemsPage<CommentsInfoItem> next = extractor.getPage(nextPage);
                errors.addAll(next.getErrors());
                items = config.filter(next.getItems());
                nextPage = next.getNextPage();
            } catch (final Exception e) {
                errors.add(e);
                break;
            }
        }
        return new InfoItemsPage<>(items, nextPage, errors);
    }

    private transient CommentsExtractor commentsExtractor;
    private boolean commentsDisabled = false;

    public CommentsExtractor getCommentsExtractor() {
        return commentsExtractor;
    }

    public void setCommentsExtractor(final CommentsExtractor commentsExtractor) {
        this.commentsExtractor = commentsExtractor;
    }

    /**
     * @apiNote Warning: This method is experimental and may get removed in a future release.
     * @return {@code true} if the comments are disabled otherwise {@code false} (default)
     * @see CommentsExtractor#isCommentsDisabled()
     */
    public boolean isCommentsDisabled() {
        return commentsDisabled;
    }

    /**
     * @apiNote Warning: This method is experimental and may get removed in a future release.
     * @param commentsDisabled {@code true} if the comments are disabled otherwise {@code false}
     */
    public void setCommentsDisabled(final boolean commentsDisabled) {
        this.commentsDisabled = commentsDisabled;
    }
}
