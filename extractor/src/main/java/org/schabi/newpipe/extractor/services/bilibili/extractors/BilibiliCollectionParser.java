package org.schabi.newpipe.extractor.services.bilibili.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.services.bilibili.utils;
import org.schabi.newpipe.extractor.stream.StreamCollectionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BilibiliCollectionParser {
    private BilibiliCollectionParser() {
    }

    static List<StreamCollectionInfo> parse(final JsonObject watch) {
        if (watch == null) {
            return Collections.emptyList();
        }

        final JsonObject season = watch.getObject("ugc_season");
        if (season == null) {
            return Collections.emptyList();
        }

        final String collectionId = numericId(season, "id");
        final String title = season.getString("title");
        if (collectionId == null || title == null || title.isEmpty()) {
            return Collections.emptyList();
        }

        final List<StreamCollectionInfo.Section> sections = new ArrayList<>();
        final JsonArray sectionData = season.getArray("sections");
        if (sectionData != null) {
            for (int sectionIndex = 0; sectionIndex < sectionData.size(); sectionIndex++) {
                final JsonObject section = sectionData.getObject(sectionIndex);
                if (section == null) {
                    continue;
                }
                final String sectionId = numericId(section, "id");
                final String sectionTitle = section.getString("title");
                if (sectionId == null || sectionTitle == null) {
                    continue;
                }
                sections.add(new StreamCollectionInfo.Section(sectionId, sectionTitle,
                        parseEpisodes(section.getArray("episodes"))));
            }
        }
        return Collections.singletonList(new StreamCollectionInfo(collectionId, title, sections));
    }

    private static List<StreamCollectionInfo.Episode> parseEpisodes(final JsonArray episodeData) {
        if (episodeData == null) {
            return Collections.emptyList();
        }

        final List<StreamCollectionInfo.Episode> episodes = new ArrayList<>();
        for (int i = 0; i < episodeData.size(); i++) {
            final JsonObject episode = episodeData.getObject(i);
            if (episode == null) {
                continue;
            }
            final JsonObject archive = episode.getObject("arc");
            final long aid = episode.getLong("aid", archive == null ? -1 : archive.getLong("aid", -1));
            final long cid = episode.getLong("cid", -1);
            final String videoId = episode.getString("bvid",
                    archive == null ? null : archive.getString("bvid"));
            final String resolvedVideoId = videoId == null || videoId.isEmpty()
                    ? (aid > 0 ? utils.av2bv(aid) : null)
                    : videoId;
            String title = episode.getString("title");
            if ((title == null || title.isEmpty()) && archive != null) {
                title = archive.getString("title");
            }
            String thumbnailUrl = episode.getString("pic");
            if ((thumbnailUrl == null || thumbnailUrl.isEmpty()) && archive != null) {
                thumbnailUrl = archive.getString("pic");
            }
            if (thumbnailUrl != null) {
                thumbnailUrl = thumbnailUrl.replace("http:", "https:");
            }
            if (resolvedVideoId == null || title == null || title.isEmpty()) {
                continue;
            }
            final String url = "https://www.bilibili.com/video/" + resolvedVideoId;
            episodes.add(new StreamCollectionInfo.Episode(resolvedVideoId, title, url, cid,
                    thumbnailUrl));
        }
        return episodes;
    }

    private static String numericId(final JsonObject object, final String key) {
        final long value = object.getLong(key, -1);
        return value < 0 ? null : Long.toString(value);
    }
}
