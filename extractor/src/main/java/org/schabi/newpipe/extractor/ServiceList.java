package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/*
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * ServiceList.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Registry of the available services: the built-in ones plus any service registered at
 * runtime by a plugin host through {@link #register(StreamingService)}.
 *
 * <p>Service ids are not list indexes. Ids 1 to 4 belonged to services that are now
 * distributed as plugins and are kept reserved for them.</p>
 */
@SuppressWarnings({"ConstantName", "InnerAssignment"}) // keep unusual names and inner assignments
public final class ServiceList {
    private ServiceList() {
        //no instance
    }

    public static final YoutubeService YouTube;
    public static final NiconicoService NicoNico;
    public static final BilibiliService BiliBili;

    /**
     * Built-in services. When creating a new built-in service, give it the next free id
     * below {@link #FIRST_DYNAMIC_PLUGIN_ID}.
     */
    private static final List<StreamingService> BUILT_IN = Collections.unmodifiableList(
            Arrays.asList(
                    YouTube = new YoutubeService(0),
                    BiliBili = new BilibiliService(5),
                    NicoNico = new NiconicoService(6)
            ));

    /**
     * Ids assigned by hosts to plugins that were never built-in start here.
     */
    public static final int FIRST_DYNAMIC_PLUGIN_ID = 1000;

    private static final Object LOCK = new Object();
    // copy-on-write so that readers never need to lock
    private static volatile List<StreamingService> services = BUILT_IN;

    /**
     * @return an unmodifiable snapshot of all the available services
     */
    public static List<StreamingService> all() {
        return services;
    }

    public static List<StreamingService> builtIn() {
        return BUILT_IN;
    }

    /**
     * Add a service provided by a plugin.
     *
     * @throws IllegalArgumentException if a service with the same id or name already exists
     */
    public static void register(@Nonnull final StreamingService service) {
        synchronized (LOCK) {
            for (final StreamingService s : services) {
                if (s.getServiceId() == service.getServiceId()) {
                    throw new IllegalArgumentException(
                            "Service id already registered: " + service.getServiceId());
                }
                if (s.getServiceInfo().getName().equals(service.getServiceInfo().getName())) {
                    throw new IllegalArgumentException(
                            "Service name already registered: "
                                    + service.getServiceInfo().getName());
                }
            }
            final List<StreamingService> copy = new ArrayList<>(services);
            copy.add(service);
            services = Collections.unmodifiableList(copy);
        }
    }

    /**
     * Remove a service previously added with {@link #register(StreamingService)}.
     * Built-in services cannot be removed.
     */
    public static void unregister(final int serviceId) {
        synchronized (LOCK) {
            final List<StreamingService> copy = new ArrayList<>(services);
            copy.removeIf(s -> s.getServiceId() == serviceId && !BUILT_IN.contains(s));
            services = Collections.unmodifiableList(copy);
        }
    }

    @Nullable
    public static StreamingService getById(final int serviceId) {
        for (final StreamingService s : services) {
            if (s.getServiceId() == serviceId) {
                return s;
            }
        }
        return null;
    }
}
