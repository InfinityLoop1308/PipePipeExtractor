package org.schabi.newpipe.extractor;

import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * One server instance of a service, for example one PeerTube instance.
 *
 * <p>Instances are identified by their url, so two objects with the same url are equal even
 * if their names differ.</p>
 */
public final class ServiceInstance {

    @Nonnull
    private final String name;
    @Nonnull
    private final String url;

    /**
     * @param name the name shown to the user, for example the instance name returned by the
     *             service or its host
     * @param url  the base url of the instance, without a trailing slash
     */
    public ServiceInstance(@Nonnull final String name, @Nonnull final String url) {
        this.name = Objects.requireNonNull(name);
        this.url = Objects.requireNonNull(url);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceInstance)) {
            return false;
        }
        return url.equals(((ServiceInstance) o).url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + url + ")";
    }
}
