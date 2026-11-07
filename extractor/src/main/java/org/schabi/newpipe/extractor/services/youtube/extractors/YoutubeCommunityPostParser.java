package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.utils.Utils;
import org.jsoup.nodes.Entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;

/** Converts the post renderers returned by YouTube's channel posts tab. */
final class YoutubeCommunityPostParser {
    private YoutubeCommunityPostParser() { }

    static CommunityPostInfoItem parse(final int serviceId, final JsonObject post)
            throws ParsingException {
        return parse(serviceId, post, null);
    }

    static CommunityPostInfoItem parse(final int serviceId, final JsonObject post,
                                       final TimeAgoParser timeAgoParser)
            throws ParsingException {
        final String id = post.getString("postId", "");
        if (id.isEmpty()) {
            throw new ParsingException("YouTube community post has no postId");
        }
        final String author = text(post.getObject("authorText"));
        final String channelId = post.getObject("authorEndpoint")
                .getObject("browseEndpoint").getString("browseId", "");
        final String authorUrl = channelId.isEmpty() ? ""
                : "https://www.youtube.com/channel/" + channelId;
        final List<Image> avatars = images(post.getObject("authorThumbnail"));
        final String date = text(post.getObject("publishedTimeText"));
        final String likes = text(post.getObject("voteCount"));
        final String comments = text(post.getObject("actionButtons")
                .getObject("commentActionButtonsRenderer").getObject("replyButton")
                .getObject("buttonRenderer").getObject("text"));
        final String content = html(post.getObject("contentText"));
        final JsonObject attachment = post.getObject("backstageAttachment");
        final List<List<Image>> media = new ArrayList<>();
        final List<CommunityPostInfoItem.PollChoice> choices = new ArrayList<>();
        String votes = "";
        CommunityPostInfoItem.PollType pollType = CommunityPostInfoItem.PollType.UNKNOWN;
        String pollStatus = "";
        String sharedUrl = "";
        String sharedTitle = "";
        List<Image> sharedPreviewImages = Collections.emptyList();
        CommunityPostInfoItem sharedPost = null;
        CommunityPostInfoItem.PostType type = CommunityPostInfoItem.PostType.TEXT;

        if (attachment.has("backstageImageRenderer")) {
            type = CommunityPostInfoItem.PostType.IMAGE;
            addImage(media, attachment.getObject("backstageImageRenderer"));
        } else if (attachment.has("postMultiImageRenderer")) {
            type = CommunityPostInfoItem.PostType.IMAGE;
            for (final Object entry : attachment.getObject("postMultiImageRenderer")
                    .getArray("images")) {
                if (entry instanceof JsonObject) {
                    addImage(media, ((JsonObject) entry).getObject("backstageImageRenderer"));
                }
            }
        } else if (attachment.has("pollRenderer")) {
            type = CommunityPostInfoItem.PostType.POLL;
            final JsonObject poll = attachment.getObject("pollRenderer");
            for (final Object entry : poll.getArray("choices")) {
                if (entry instanceof JsonObject) {
                    final JsonObject choice = (JsonObject) entry;
                    choices.add(new CommunityPostInfoItem.PollChoice(
                            text(choice.getObject("text")), images(choice.getObject("image"))));
                }
            }
            votes = text(poll.getObject("totalVotes"));
            pollType = parsePollType(poll.getString("type", ""));
            pollStatus = post.getString("pollStatus", "");
        } else if (attachment.has("videoRenderer")) {
            type = CommunityPostInfoItem.PostType.VIDEO;
            final JsonObject video = attachment.getObject("videoRenderer");
            final String videoId = video.getString("videoId", "");
            if (!videoId.isEmpty()) {
                sharedUrl = "https://www.youtube.com/watch?v=" + videoId;
            }
            sharedTitle = text(video.getObject("title"));
            sharedPreviewImages = images(video.getObject("thumbnail"));
        } else if (attachment.has("sharedPostRenderer")) {
            type = CommunityPostInfoItem.PostType.SHARED_POST;
            final JsonObject original = attachment.getObject("sharedPostRenderer")
                    .getObject("originalPost");
            final JsonObject renderer = original.has("backstagePostRenderer")
                    ? original.getObject("backstagePostRenderer") : original.getObject("postRenderer");
            if (!renderer.getString("postId", "").isEmpty()) {
                sharedPost = parse(serviceId, renderer, timeAgoParser);
                sharedUrl = sharedPost.getUrl();
            }
        } else if (!attachment.isEmpty()) {
            type = CommunityPostInfoItem.PostType.UNKNOWN;
        }
        return new CommunityPostInfoItem(serviceId, id, "https://www.youtube.com/post/" + id,
                new Description(content == null ? "" : content, Description.HTML),
                text(post.getObject("contentText")), author, channelId, authorUrl, avatars,
                date, parseDate(timeAgoParser, date), likes, parseCount(likes),
                comments, parseCount(comments), type, media, choices, pollType, pollStatus,
                votes, parseCount(votes), sharedUrl,
                sharedTitle, sharedPreviewImages, sharedPost);
    }

    static CommunityPostInfoItem parseShared(final int serviceId, final JsonObject post)
            throws ParsingException {
        return parseShared(serviceId, post, null);
    }

    static CommunityPostInfoItem parseShared(final int serviceId, final JsonObject post,
                                             final TimeAgoParser timeAgoParser)
            throws ParsingException {
        final String id = post.getString("postId", "");
        if (id.isEmpty()) {
            throw new ParsingException("YouTube shared post has no postId");
        }
        final JsonObject original = post.getObject("originalPost");
        final JsonObject renderer = original.has("backstagePostRenderer")
                ? original.getObject("backstagePostRenderer") : original.getObject("postRenderer");
        final CommunityPostInfoItem shared = renderer.getString("postId", "").isEmpty()
                ? null : parse(serviceId, renderer, timeAgoParser);
        final String author = text(post.getObject("displayName"));
        final String authorId = post.getObject("endpoint").getObject("browseEndpoint")
                .getString("browseId", "");
        final String content = html(post.getObject("content"));
        final String date = text(post.getObject("publishedTimeText"));
        return new CommunityPostInfoItem(serviceId, id, "https://www.youtube.com/post/" + id,
                new Description(content == null ? "" : content, Description.HTML),
                text(post.getObject("content")), author, authorId,
                authorId.isEmpty() ? "" : "https://www.youtube.com/channel/" + authorId,
                images(post.getObject("thumbnail")), date, parseDate(timeAgoParser, date),
                "", CommunityPostInfoItem.NO_COUNT, "", CommunityPostInfoItem.NO_COUNT,
                CommunityPostInfoItem.PostType.SHARED_POST, Collections.emptyList(),
                Collections.emptyList(), CommunityPostInfoItem.PollType.UNKNOWN, "",
                "", CommunityPostInfoItem.NO_COUNT, shared == null ? "" : shared.getUrl(),
                "", Collections.emptyList(), shared);
    }

    private static CommunityPostInfoItem.PollType parsePollType(final String type) {
        if (type.contains("QUIZ")) {
            return CommunityPostInfoItem.PollType.QUIZ;
        } else if (type.contains("IMAGE")) {
            return CommunityPostInfoItem.PollType.IMAGE;
        } else if (type.contains("TEXT")) {
            return CommunityPostInfoItem.PollType.TEXT;
        }
        return CommunityPostInfoItem.PollType.UNKNOWN;
    }

    private static long parseCount(final String count) {
        if (count.isEmpty()) {
            return CommunityPostInfoItem.NO_COUNT;
        }
        try {
            return Utils.mixedNumberWordToLong(count);
        } catch (final Exception ignored) {
            return CommunityPostInfoItem.NO_COUNT;
        }
    }

    private static DateWrapper parseDate(final TimeAgoParser parser, final String text) {
        if (parser == null || text.isEmpty()) return null;
        try {
            return parser.parse(text);
        } catch (final ParsingException ignored) {
            return null;
        }
    }

    private static String text(final JsonObject object) throws ParsingException {
        final String result = getTextFromObject(normalizeRuns(object));
        return result == null ? "" : result;
    }

    private static String html(final JsonObject object) throws ParsingException {
        if (object.has("simpleText")) {
            return Entities.escape(object.getString("simpleText", "")).replace("\n", "<br>");
        }
        try {
            final String result = getTextFromObject(normalizeRuns(object), true);
            return result == null ? "" : result;
        } catch (final ParsingException error) {
            return Entities.escape(text(object)).replace("\n", "<br>");
        }
    }

    private static JsonObject normalizeRuns(final JsonObject object) {
        if (object.has("simpleText") || !object.has("runs")) return object;
        final JsonArray runs = new JsonArray();
        for (final Object entry : object.getArray("runs")) {
            if (!(entry instanceof JsonObject)) continue;
            final JsonObject run = new JsonObject();
            run.putAll((JsonObject) entry);
            if (!run.has("text") || run.getString("text") == null) {
                final JsonObject emoji = run.getObject("emoji");
                final JsonArray shortcuts = emoji.getArray("shortcuts");
                run.put("text", shortcuts.isEmpty() ? emoji.getString("emojiId", "")
                        : shortcuts.getString(0, ""));
            }
            runs.add(run);
        }
        final JsonObject normalized = new JsonObject();
        normalized.put("runs", runs);
        return normalized;
    }

    private static List<Image> images(final JsonObject image) throws ParsingException {
        JsonArray variants = image.getArray("thumbnails");
        if (variants.isEmpty()) variants = image.getArray("sources");
        return variants.isEmpty() ? Collections.emptyList()
                : getImagesFromThumbnailsArray(variants);
    }

    private static void addImage(final List<List<Image>> media, final JsonObject renderer)
            throws ParsingException {
        final List<Image> variants = images(renderer.getObject("image"));
        if (!variants.isEmpty()) {
            media.add(variants);
        }
    }
}
