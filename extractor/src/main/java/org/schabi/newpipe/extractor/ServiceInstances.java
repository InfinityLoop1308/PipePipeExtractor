package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional capability of a {@link StreamingService} whose content is served by one of several
 * interchangeable instances, for example a federated service such as PeerTube.
 *
 * <p>Hosts detect this interface with {@code instanceof} and show an instance selector only
 * for the services that implement it. The service owns the instance list and persists it
 * itself, normally through its plugin preferences, so hosts never need to know its storage
 * format.</p>
 */
public interface ServiceInstances {

    /**
     * @return the saved instances in display order, never empty; when nothing was saved yet
     * this contains the current or default instance
     */
    @Nonnull
    List<ServiceInstance> getInstances();

    /**
     * @return the instance the service currently requests content from
     */
    @Nonnull
    ServiceInstance getCurrentInstance();

    /**
     * Validate an instance url and add it to the saved instances.
     *
     * <p>Validation usually needs the network, so hosts must call this method from a
     * background thread.</p>
     *
     * @param url the base url of the instance
     * @return the added instance, with the name reported by the instance itself; if an
     * instance with the same url is already saved, that instance is returned unchanged
     * @throws ExtractionException if the url is invalid or the instance cannot be reached
     */
    @Nonnull
    ServiceInstance addInstance(@Nonnull String url) throws ExtractionException;

    /**
     * Make an instance the current one and persist the choice. If the instance is not saved
     * yet, it is added to the saved instances.
     *
     * @param instance the instance to use from now on
     */
    void selectInstance(@Nonnull ServiceInstance instance);

    /**
     * Replace the saved instances, for example after the user reordered or removed some.
     * The current instance is kept, even if it is not part of the given list. Passing an
     * empty list resets the saved instances and the current instance to the default.
     *
     * @param instances the new list in display order
     */
    void setInstances(@Nonnull List<ServiceInstance> instances);

    /**
     * @return a web page that lists public instances of the service, or null if the service
     * has no such page
     */
    @Nullable
    String getInstancesHelpUrl();
}
