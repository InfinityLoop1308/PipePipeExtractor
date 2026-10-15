package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;

public class BilibiliPlaylistLinkHandlerFactory extends ListLinkHandlerFactory {

    public static class PlaylistTarget {
        public final String type; // "seasons_archives", "series", or "partition"
        public final String mid;
        public final String id;

        public PlaylistTarget(String type, String mid, String id) {
            this.type = type;
            this.mid = mid != null ? mid : "";
            this.id = id != null ? id : "";
        }
    }

    public static PlaylistTarget parseTarget(String url) throws ParsingException {
        if (url == null || url.trim().isEmpty()) {
            throw new ParsingException("URL cannot be empty");
        }
        String trimmed = url.trim();

        // 1. Partition URL
        if (trimmed.contains(GET_PARTITION_URL)) {
            String bvid = "";
            if (trimmed.contains("bvid=")) {
                bvid = trimmed.split("bvid=")[1].split("&")[0];
            }
            return new PlaylistTarget("partition", "", bvid);
        }

        // 2. Custom URI schemes
        if (trimmed.startsWith("bilibili:season:")) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 4) {
                return new PlaylistTarget("seasons_archives", parts[2], parts[3]);
            } else if (parts.length == 3) {
                return new PlaylistTarget("seasons_archives", "", parts[2]);
            }
        }
        if (trimmed.startsWith("bilibili:series:")) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 4) {
                return new PlaylistTarget("series", parts[2], parts[3]);
            } else if (parts.length == 3) {
                return new PlaylistTarget("series", "", parts[2]);
            }
        }
        if (trimmed.startsWith("season:")) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 3) {
                return new PlaylistTarget("seasons_archives", parts[1], parts[2]);
            } else if (parts.length == 2) {
                return new PlaylistTarget("seasons_archives", "", parts[1]);
            }
        }
        if (trimmed.startsWith("series:")) {
            String[] parts = trimmed.split(":");
            if (parts.length >= 3) {
                return new PlaylistTarget("series", parts[1], parts[2]);
            } else if (parts.length == 2) {
                return new PlaylistTarget("series", "", parts[1]);
            }
        }

        // 3. Legacy API URLs
        if (trimmed.contains(GET_SEASON_ARCHIVES_ARCHIVE_BASE_URL)) {
            String mid = getParam(trimmed, "mid");
            String sid = getParam(trimmed, "season_id");
            if (sid != null) {
                return new PlaylistTarget("seasons_archives", mid, sid);
            }
        }
        if (trimmed.contains(GET_SERIES_BASE_URL)) {
            String mid = getParam(trimmed, "mid");
            String sid = getParam(trimmed, "series_id");
            if (sid != null) {
                return new PlaylistTarget("series", mid, sid);
            }
        }

        // 4. Web collectiondetail & seriesdetail
        if (trimmed.contains("/channel/collectiondetail")) {
            String mid = extractMidFromPath(trimmed);
            if (mid == null || mid.isEmpty()) {
                mid = getParam(trimmed, "mid");
            }
            String sid = getParam(trimmed, "sid");
            if (sid == null) sid = getParam(trimmed, "season_id");
            if (sid != null) {
                return new PlaylistTarget("seasons_archives", mid, sid);
            }
        }
        if (trimmed.contains("/channel/seriesdetail")) {
            String mid = extractMidFromPath(trimmed);
            if (mid == null || mid.isEmpty()) {
                mid = getParam(trimmed, "mid");
            }
            String sid = getParam(trimmed, "sid");
            if (sid == null) sid = getParam(trimmed, "series_id");
            if (sid != null) {
                return new PlaylistTarget("series", mid, sid);
            }
        }

        // 5. Web medialist URL: /medialist/play/...
        if (trimmed.contains("/medialist/play/")) {
            String mid = extractMidAfterPlay(trimmed);
            if (mid == null || mid.isEmpty()) {
                mid = getParam(trimmed, "mid");
            }
            String business = getParam(trimmed, "business");
            String businessId = getParam(trimmed, "business_id");
            if (businessId == null) businessId = getParam(trimmed, "sid");
            if (businessId == null) businessId = getParam(trimmed, "season_id");
            if (businessId == null) businessId = getParam(trimmed, "series_id");

            if ("space_series".equalsIgnoreCase(business)) {
                return new PlaylistTarget("series", mid, businessId != null ? businessId : "");
            } else {
                return new PlaylistTarget("seasons_archives", mid, businessId != null ? businessId : "");
            }
        }

        throw new ParsingException("Not a supported Bilibili playlist URL: " + url);
    }

    private static String getParam(String url, String key) {
        Pattern pattern = Pattern.compile("[?&]" + Pattern.quote(key) + "=([^&#]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                return URLDecoder.decode(matcher.group(1), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String extractMidFromPath(String url) {
        Pattern pattern = Pattern.compile("space\\.bilibili\\.com/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractMidAfterPlay(String url) {
        Pattern pattern = Pattern.compile("/medialist/play/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public String getId(String url) throws ParsingException {
        PlaylistTarget target = parseTarget(url);
        if ("partition".equals(target.type)) {
            return url;
        }
        String prefix = "series".equals(target.type) ? "series:" : "season:";
        if (!target.mid.isEmpty()) {
            return prefix + target.mid + ":" + target.id;
        } else {
            return prefix + target.id;
        }
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        try {
            parseTarget(url);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String id, List<FilterItem> contentFilter, List<FilterItem> sortFilter) throws ParsingException {
        if (id == null) return null;
        if (id.startsWith("http://") || id.startsWith("https://")) {
            return id;
        }
        if (id.startsWith("bilibili:")) {
            id = id.substring("bilibili:".length());
        }
        if (id.startsWith("season:")) {
            String[] parts = id.split(":");
            if (parts.length >= 3) {
                return "https://www.bilibili.com/medialist/play/" + parts[1] + "?business=space_collection&business_id=" + parts[2];
            } else if (parts.length == 2) {
                return "https://www.bilibili.com/medialist/play/?business=space_collection&business_id=" + parts[1];
            }
        }
        if (id.startsWith("series:")) {
            String[] parts = id.split(":");
            if (parts.length >= 3) {
                return "https://www.bilibili.com/medialist/play/" + parts[1] + "?business=space_series&business_id=" + parts[2];
            } else if (parts.length == 2) {
                return "https://www.bilibili.com/medialist/play/?business=space_series&business_id=" + parts[1];
            }
        }
        return id;
    }
}
