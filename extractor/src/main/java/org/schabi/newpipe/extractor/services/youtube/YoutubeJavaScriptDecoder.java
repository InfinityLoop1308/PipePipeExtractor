package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface YoutubeJavaScriptDecoder {
    @Nonnull
    YoutubeApiDecoder.BatchDecodeResult decodeBatch(
            @Nonnull String playerId,
            @Nullable List<String> signatures,
            @Nullable List<String> throttlingParameters) throws ParsingException;
}
