package org.schabi.newpipe.extractor.comments;

import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.stream.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * User-defined rules for hiding comments: by keywords in the comment text,
 * by the commenter's name, or by a minimum like count.
 */
public class CommentsFilterConfig {
    private final List<String> keywords = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();
    private final int minLikeCount;

    public CommentsFilterConfig(final List<String> keywords,
                                final List<String> usernames,
                                final int minLikeCount) {
        if (keywords != null) {
            for (final String keyword : keywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    this.keywords.add(keyword.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (usernames != null) {
            for (final String username : usernames) {
                final String normalized = normalizeUsername(username);
                if (!normalized.isEmpty()) {
                    this.usernames.add(normalized);
                }
            }
        }
        this.minLikeCount = Math.max(0, minLikeCount);
    }

    public boolean isEmpty() {
        return keywords.isEmpty() && usernames.isEmpty() && minLikeCount <= 0;
    }

    public boolean shouldFilter(final CommentsInfoItem item) {
        if (item == null) {
            return false;
        }

        // Like count filter. Unknown like counts are kept, and pinned comments are exempt
        // so that the uploader's pinned comment is still visible on new videos.
        if (minLikeCount > 0 && !item.isPinned()
                && item.getLikeCount() != CommentsInfoItem.NO_LIKE_COUNT
                && item.getLikeCount() >= 0
                && item.getLikeCount() < minLikeCount) {
            return true;
        }

        if (!usernames.isEmpty()) {
            final String uploader = normalizeUsername(item.getUploaderName());
            if (!uploader.isEmpty() && usernames.contains(uploader)) {
                return true;
            }
        }

        if (!keywords.isEmpty()) {
            final String text = getPlainText(item.getCommentText());
            if (!text.isEmpty()) {
                for (final String keyword : keywords) {
                    if (text.contains(keyword)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public List<CommentsInfoItem> filter(final List<CommentsInfoItem> items) {
        if (items == null || isEmpty()) {
            return items;
        }
        final List<CommentsInfoItem> result = new ArrayList<>(items.size());
        for (final CommentsInfoItem item : items) {
            if (!shouldFilter(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static String normalizeUsername(final String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim();
        // YouTube shows handles as "@name"; match with or without the leading "@"
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String getPlainText(final Description description) {
        if (description == null || description.getContent() == null) {
            return "";
        }
        String content = description.getContent();
        if (description.getType() == Description.HTML) {
            content = Jsoup.parse(content).text();
        }
        return content.toLowerCase(Locale.ROOT);
    }
}
