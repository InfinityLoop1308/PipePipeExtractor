package org.schabi.newpipe.extractor.bulletComments;

import java.time.Duration;

import org.schabi.newpipe.extractor.InfoItem;

public class BulletCommentsInfoItem extends InfoItem {
    public enum Position {
        REGULAR,
        BOTTOM,
        TOP,
    }

    private String commentText;
    private int argbColor;
    private Position position;
    private double relativeFontSize;
    private Duration duration;

    public BulletCommentsInfoItem(final int serviceId, final String url, final String name) {
        super(InfoType.COMMENT, serviceId, url, name);
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(final String commentText) {
        this.commentText = commentText;
    }

    public int getArgbColor() {
        return argbColor;
    }

    public void setArgbColor(final int argbColor) {
        this.argbColor = argbColor;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(final Position position) {
        this.position = position;
    }

    public double getRelativeFontSize() {
        return relativeFontSize;
    }

    public void setRelativeFontSize(final double relativeFontSize) {
        this.relativeFontSize = relativeFontSize;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(final Duration duration) {
        this.duration = duration;
    }
}