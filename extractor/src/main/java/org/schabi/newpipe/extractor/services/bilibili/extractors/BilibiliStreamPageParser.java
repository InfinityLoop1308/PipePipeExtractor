package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.Utils;

import java.net.MalformedURLException;

final class BilibiliStreamPageParser {
    private BilibiliStreamPageParser() {
    }

    static JsonObject pageForUrl(final JsonObject watch, final String streamUrl)
            throws ParsingException {
        final JsonArray pages = watch == null ? null : watch.getArray("pages");
        if (pages == null || pages.isEmpty()) {
            throw new ParsingException("BiliBili video has no pages");
        }

        final String pageNumberValue;
        try {
            pageNumberValue = Utils.getQueryValue(Utils.stringToURL(streamUrl), "p");
        } catch (MalformedURLException e) {
            throw new ParsingException("Invalid BiliBili video URL", e);
        }
        final int pageNumber;
        try {
            pageNumber = pageNumberValue == null ? 1 : Integer.parseInt(pageNumberValue);
        } catch (NumberFormatException e) {
            throw new ParsingException("Invalid BiliBili page number: " + pageNumberValue, e);
        }
        if (pageNumber < 1 || pageNumber > pages.size()) {
            throw new ParsingException("BiliBili page number is out of range: " + pageNumber);
        }
        return pages.getObject(pageNumber - 1);
    }
}
