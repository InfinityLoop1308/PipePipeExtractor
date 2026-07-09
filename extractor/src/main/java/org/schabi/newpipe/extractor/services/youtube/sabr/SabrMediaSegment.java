package org.schabi.newpipe.extractor.services.youtube.sabr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SabrMediaSegment {
    private static final int COPY_BUFFER_SIZE = 8192;

    @Nonnull
    private final SabrMediaHeader header;
    @Nullable
    private final byte[] data;
    @Nullable
    private final File file;
    private final int length;

    SabrMediaSegment(@Nonnull final SabrMediaHeader header, @Nonnull final byte[] data) {
        this.header = header;
        // No defensive copy: the collector hands over a freshly built array it does not retain.
        // Media segments reach several MB (4K), so cloning here doubled peak memory and caused OOM
        // under rapid switching. The array is treated as immutable from here on.
        this.data = data;
        this.file = null;
        this.length = data.length;
    }

    SabrMediaSegment(@Nonnull final SabrMediaHeader header,
                     @Nonnull final File file,
                     final int length) {
        this.header = header;
        this.data = null;
        this.file = file;
        this.length = length;
    }

    @Nonnull
    public SabrMediaHeader getHeader() {
        return header;
    }

    /**
     * Read-only: callers must not mutate the returned array. Disk-backed segments are loaded only
     * for legacy callers; playback should use {@link #openStream()} to avoid pulling large media
     * segments back onto the Java heap.
     */
    @Nonnull
    public byte[] getData() {
        if (data != null) {
            return data;
        }
        try (InputStream input = openStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(length)) {
            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (final IOException e) {
            throw new IllegalStateException("Could not read disk-backed SABR media segment", e);
        }
    }

    @Nonnull
    public InputStream openStream() throws IOException {
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        if (file == null) {
            throw new IOException("SABR media segment has no backing data");
        }
        return new FileInputStream(file);
    }

    public boolean isDiskBacked() {
        return file != null;
    }

    public void delete() {
        if (file != null && !file.delete() && file.exists()) {
            file.deleteOnExit();
        }
    }

    public int getLength() {
        return length;
    }

    @Nullable
    File getFile() {
        return file;
    }
}
