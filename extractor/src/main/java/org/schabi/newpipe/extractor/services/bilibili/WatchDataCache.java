package org.schabi.newpipe.extractor.services.bilibili;

public class WatchDataCache {
    private int cid;
    WatchDataCache(){
        this.cid = 0;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }
}
