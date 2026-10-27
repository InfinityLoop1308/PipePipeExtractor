package org.schabi.newpipe.extractor.stream;

import java.io.Serializable;
import java.util.List;

/** A collection of streams associated with an opened stream. */
public final class StreamCollectionInfo implements Serializable {
    private final String id;
    private final String title;
    private final List<Section> sections;

    public StreamCollectionInfo(final String id, final String title,
                                final List<Section> sections) {
        this.id = id;
        this.title = title;
        this.sections = List.copyOf(sections);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Section> getSections() {
        return sections;
    }

    public static final class Section implements Serializable {
        private final String id;
        private final String title;
        private final List<Episode> episodes;

        public Section(final String id, final String title, final List<Episode> episodes) {
            this.id = id;
            this.title = title;
            this.episodes = List.copyOf(episodes);
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public List<Episode> getEpisodes() {
            return episodes;
        }
    }

    public static final class Episode implements Serializable {
        private final String videoId;
        private final String title;
        private final String url;
        private final long contentId;
        private final String thumbnailUrl;

        public Episode(final String videoId, final String title, final String url,
                       final long contentId) {
            this(videoId, title, url, contentId, "");
        }

        public Episode(final String videoId, final String title, final String url,
                       final long contentId, final String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.url = url;
            this.contentId = contentId;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
        }

        public String getVideoId() {
            return videoId;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public long getContentId() {
            return contentId;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }
}
