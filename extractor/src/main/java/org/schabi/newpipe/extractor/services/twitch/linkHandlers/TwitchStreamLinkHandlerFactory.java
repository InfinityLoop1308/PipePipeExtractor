package org.schabi.newpipe.extractor.services.twitch.linkHandlers;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlBuilder;
import org.schabi.newpipe.extractor.services.twitch.TwitchUrlParser;
import org.schabi.newpipe.extractor.services.twitch.data.TwitchStreamLinkType;

public class TwitchStreamLinkHandlerFactory extends LinkHandlerFactory {

    private TwitchStreamLinkType type;

    @Override
    public String getId(final String urlString) throws ParsingException, UnsupportedOperationException {
//        return switch (type) {
//            case CLIP -> TwitchUrlParser.parseClipIdFromClipUrl(urlString);
//            case STREAM -> TwitchUrlParser.parse(urlString);
//            case VOD -> TwitchUrlParser.parseVodIdFromVodUrl(urlString);
//        };
        return urlString;
    }

    @Override
    public String getUrl(final String id) throws ParsingException, UnsupportedOperationException {
//        return switch (type) {
//            case CLIP -> TwitchUrlBuilder.buildClipUrlFromClipId(id);
//            case STREAM -> TwitchUrlBuilder.buildStreamUrlFromChannelName(id);
//            case VOD -> TwitchUrlBuilder.buildVodUrlFromVodId(id);
//        };
        return id;
    }

    @Override
    public boolean onAcceptUrl(final String urlString) throws ParsingException {
        try {
            TwitchUrlParser.parseClipIdFromClipUrl(urlString);
            type = TwitchStreamLinkType.CLIP;
            return true;
        } catch (Exception ignored) {
        }
        try {
            TwitchUrlParser.parseVodIdFromVodUrl(urlString);
            type = TwitchStreamLinkType.VOD;
            return true;
        } catch (Exception ignored) {
        }
        try {
            TwitchUrlParser.parseChannelUrl(urlString);
            type = TwitchStreamLinkType.STREAM;
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }
}
