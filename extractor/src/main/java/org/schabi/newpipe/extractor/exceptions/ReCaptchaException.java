package org.schabi.newpipe.extractor.exceptions;

/*
 * Created by beneth <bmauduit@beneth.fr> on 07.12.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ReCaptchaException.java is part of NewPipe.
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
 * The service blocked the request with a challenge the user can solve in a WebView, at
 * {@link #getChallengeUrl()}.
 */
public class ReCaptchaException extends AntiBotException {
    private final String challengeUrl;

    /**
     * @param url          the blocked request
     * @param challengeUrl the page showing the challenge, which may be on another site than
     *                     the blocked request (e.g. Google's bot check for YouTube)
     */
    public ReCaptchaException(final int serviceId, final String message, final String url,
                              final String challengeUrl) {
        super(serviceId, message, url, null);
        this.challengeUrl = challengeUrl;
    }

    /** For a challenge shown at the blocked url itself. */
    public ReCaptchaException(final int serviceId, final String message, final String url) {
        this(serviceId, message, url, url);
    }

    /** For callers that do not know the service, e.g. the downloader. */
    public ReCaptchaException(final String message, final String url) {
        this(-1, message, url);
    }

    public String getChallengeUrl() {
        return challengeUrl;
    }
}
