package org.schabi.newpipe.extractor.community;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.Description;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A read-only community post returned in a channel tab.
 *
 * <p>The name is the plain-text content; {@link #getContent()} retains supported formatting.
 * Image variants are grouped by attachment rather than flattened, so multiple images in a post
 * remain distinguishable. Missing optional data is represented by empty values.</p>
 */
public final class CommunityPostInfoItem extends InfoItem {
    public enum PostType { TEXT, IMAGE, POLL, VIDEO, SHARED_POST, UNKNOWN }
    public enum PollType { TEXT, IMAGE, QUIZ, UNKNOWN }

    public static final long NO_COUNT = -1;

    /** A poll option; image variants may be empty for text-only options. */
    public static final class PollChoice implements Serializable {
        private final String text;
        private final List<Image> images;

        public PollChoice(final String text, final List<Image> images) {
            this.text = text;
            this.images = Collections.unmodifiableList(new ArrayList<>(images));
        }

        public String getText() {
            return text;
        }

        public List<Image> getImages() {
            return images;
        }
    }

    private final String postId;
    private final Description content;
    private final String authorName;
    private final String authorId;
    private final String authorUrl;
    private final List<Image> authorAvatars;
    private final String textualUploadDate;
    private final String textualLikeCount;
    private final String textualCommentCount;
    private final long likeCount;
    private final long commentCount;
    @Nullable
    private final DateWrapper uploadDate;
    private final PostType postType;
    private final List<List<Image>> images;
    private final List<PollChoice> pollChoices;
    private final PollType pollType;
    private final String pollStatus;
    private final String textualPollVotes;
    private final long pollVoteCount;
    private final String sharedUrl;
    private final String sharedTitle;
    private final List<Image> sharedPreviewImages;
    @Nullable
    private final CommunityPostInfoItem sharedPost;

    public CommunityPostInfoItem(final int serviceId, final String postId, final String postUrl,
                                 final Description content, final String plainText,
                                 final String authorName, final String authorId,
                                 final String authorUrl, final List<Image> authorAvatars,
                                 final String textualUploadDate, @Nullable final DateWrapper uploadDate,
                                 final String textualLikeCount, final long likeCount,
                                 final String textualCommentCount, final long commentCount,
                                 final PostType postType,
                                 final List<List<Image>> images, final List<PollChoice> pollChoices,
                                 final PollType pollType, final String pollStatus,
                                 final String textualPollVotes, final long pollVoteCount,
                                 final String sharedUrl,
                                 final String sharedTitle, final List<Image> sharedPreviewImages,
                                 @Nullable final CommunityPostInfoItem sharedPost) {
        super(InfoType.COMMUNITY_POST, serviceId, postUrl, plainText);
        this.postId = postId;
        this.content = content;
        this.authorName = authorName;
        this.authorId = authorId;
        this.authorUrl = authorUrl;
        this.authorAvatars = Collections.unmodifiableList(new ArrayList<>(authorAvatars));
        this.textualUploadDate = textualUploadDate;
        this.textualLikeCount = textualLikeCount;
        this.textualCommentCount = textualCommentCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.uploadDate = uploadDate;
        this.postType = postType;
        final List<List<Image>> copies = new ArrayList<>();
        for (final List<Image> image : images) {
            copies.add(Collections.unmodifiableList(new ArrayList<>(image)));
        }
        this.images = Collections.unmodifiableList(copies);
        this.pollChoices = Collections.unmodifiableList(new ArrayList<>(pollChoices));
        this.pollType = pollType;
        this.pollStatus = pollStatus;
        this.textualPollVotes = textualPollVotes;
        this.pollVoteCount = pollVoteCount;
        this.sharedUrl = sharedUrl;
        this.sharedTitle = sharedTitle;
        this.sharedPreviewImages = Collections.unmodifiableList(new ArrayList<>(sharedPreviewImages));
        this.sharedPost = sharedPost;
    }

    public String getPostId() {
        return postId;
    }

    public Description getContent() {
        return content;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getAuthorUrl() {
        return authorUrl;
    }

    public List<Image> getAuthorAvatars() {
        return authorAvatars;
    }

    public String getTextualUploadDate() {
        return textualUploadDate;
    }

    /** Localized count as returned by the service, or empty if unavailable. */
    public String getTextualLikeCount() {
        return textualLikeCount;
    }

    /** Localized count as returned by the service, or empty if unavailable. */
    public String getTextualCommentCount() {
        return textualCommentCount;
    }

    /** Parsed count, or {@link #NO_COUNT} if the localized value could not be converted. */
    public long getLikeCount() {
        return likeCount;
    }

    /** Parsed count, or {@link #NO_COUNT} if the localized value could not be converted. */
    public long getCommentCount() {
        return commentCount;
    }

    /** A parsed, possibly approximate date, or null if the service did not provide one. */
    @Nullable
    public DateWrapper getUploadDate() {
        return uploadDate;
    }

    public PostType getPostType() {
        return postType;
    }

    public List<List<Image>> getImages() {
        return images;
    }

    public List<PollChoice> getPollChoices() {
        return pollChoices;
    }

    public PollType getPollType() {
        return pollType;
    }

    /** Raw status returned by YouTube, or an empty string when unavailable. */
    public String getPollStatus() {
        return pollStatus;
    }

    public String getTextualPollVotes() {
        return textualPollVotes;
    }

    /** Parsed count, or {@link #NO_COUNT} if the localized value could not be converted. */
    public long getPollVoteCount() {
        return pollVoteCount;
    }

    public String getSharedUrl() {
        return sharedUrl;
    }

    public String getSharedTitle() {
        return sharedTitle;
    }

    public List<Image> getSharedPreviewImages() {
        return sharedPreviewImages;
    }

    /** The original post for a repost, when its renderer was supplied. */
    @Nullable
    public CommunityPostInfoItem getSharedPost() {
        return sharedPost;
    }
}
