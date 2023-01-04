package org.schabi.newpipe.extractor.services.bilibili;

public class WatchDataCache {
    private int cid;
    private long roomId;
    private long startTime;
    private String bvid;
    WatchDataCache(){
        this.cid = 0;
        roomId = 0;
        startTime = 0;
    }

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
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
}
