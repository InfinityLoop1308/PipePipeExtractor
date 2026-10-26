package org.schabi.newpipe.extractor.services.twitch.url;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.schabi.newpipe.extractor.services.twitch.Assertions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public class UrlBuilder {

    private static final Pattern pathSegmentRegex = Pattern.compile("^[a-zA-Z0-9\\-_+]+$");
    private final List<String> path = new ArrayList<>();
    private final List<ImmutablePair<String, String>> queries = new ArrayList<>();
    private String schema, host;

    public UrlBuilder withSchema(@Nonnull final String schema) {
        this.schema = schema;
        return this;
    }

    public UrlBuilder withHost(@Nonnull final String host) {
        this.host = host;
        return this;
    }

    public UrlBuilder withPathSegments(@Nonnull final String... pathSegments) {
        this.path.clear();
        this.path.addAll(List.of(pathSegments));
        return this;
    }

    public UrlBuilder addPathSegments(@Nonnull final String... pathSegments) {
        this.path.addAll(List.of(pathSegments));
        return this;
    }

    public UrlBuilder withQuery(@Nonnull final String key, @Nonnull final String value) {
        queries.clear();
        queries.add(new ImmutablePair<>(key, value));
        return this;
    }

    public UrlBuilder addQuery(@Nonnull final String key, @Nonnull final String value) {
        queries.add((new ImmutablePair<>(key, value)));
        return this;
    }

    public String build() {
        Assertions.assertThat(() -> !StringUtils.isAllBlank(schema));
        Assertions.assertThat(() -> !StringUtils.isAllBlank(host));

        final var pathStringBuilder = new StringBuilder();

        for (final var pathSegment : path) {
            Assertions.assertThat(() -> pathSegmentRegex.matcher(pathSegment).matches());
            pathStringBuilder.append("/").append(pathSegment);
        }

        final var queryStringBuilder = new StringBuilder();

        for (int i = 0; i < queries.size(); i++) {
            var queryTuple = queries.get(i);
            var urlEncodedQueryKey = URLEncoder.encode(queryTuple.getLeft(),
                    StandardCharsets.UTF_8);
            var urlEncodedQueryValue = URLEncoder.encode(queryTuple.getRight(),
                    StandardCharsets.UTF_8);
            if (i == 0)
                queryStringBuilder.append("?");
            else
                queryStringBuilder.append("&");
            queryStringBuilder.append(urlEncodedQueryKey).append("=").append(urlEncodedQueryValue);
        }

        return schema + "://" + host + pathStringBuilder + queryStringBuilder;
    }
}
