package org.schabi.newpipe.extractor.bulletComments;

import javax.annotation.Nonnull;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.List;

public abstract class BulletCommentsExtractor extends ListExtractor<BulletCommentsInfoItem> {
    public BulletCommentsExtractor(final StreamingService service, final ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return "BulletComments";
    }

    public List<BulletCommentsInfoItem> getLiveMessages() throws ParsingException {
        return null;
    }

    public boolean isLive() {
        return false;
    }

    public void disconnect(){
    }
    public void reconnect(){

    }
    public boolean isDisabled(){
        return false;
    }
    public void setCurrentPlayPosition(long currentPlayPosition){
    }
    public void clearMappingState(){}
}
