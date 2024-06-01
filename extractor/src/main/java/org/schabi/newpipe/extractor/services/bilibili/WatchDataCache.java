package org.schabi.newpipe.extractor.services.bilibili;

public class WatchDataCache {
    private long cid;
    private long roomId;
    private long startTime;
    private String bvid;

    // Fuck you auto-enqueueing
    private long lastCid;
    private String currentUrl;
    private String lastUrl;

    public long getCid() {
        return cid;
    }

    public void setCid(long cid) {
        this.cid = cid;
    }

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setBvid(String bvid) {
        this.bvid = bvid;
    }

    public String getBvid() {
        return bvid;
    }

    public void init(String url){
        if(url.equals(currentUrl)){
            return ;
        }
        lastCid = cid;
        lastUrl = currentUrl;
        currentUrl = url;
    }

    public long getLastCid() {
        return lastCid;
    }

    public String getLastUrl() {
        return lastUrl;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }
}
