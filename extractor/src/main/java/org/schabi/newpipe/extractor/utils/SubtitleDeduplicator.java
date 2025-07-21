package org.schabi.newpipe.extractor.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

/**
 * SubtitleDeduplicator.java
 *
 * 1. This file is responsible for checking if the subtitles
 * contain any duplicate entries.
 *   a) If duplicates are found, it performs the following steps:
 *      downloads the subtitle, deduplicates it,
 *      and stores it locally.
 *   b) If no duplicates are found, no action is taken.
 *
 * 2. Core Functions:
 * - checkAndDeduplicate(): Checks for duplicate subtitles
 *   and handles downloading, deduplication, and local storage.
 *
 */

public class SubtitleDeduplicator {
    private static final String TAG = "SubtitleDeduplicator";
    public static final String LOCAL_SUBTITLE_URL_PREFIX = "file://";

    private static String subCacheDir = "subtitle_cache";

    // There are two cache paths to choose:
    // 1) Here, init to call Default;
    // 2) Other places, call NotDefault before checkAndDeduplicate().
    private static File CACHE_DIR = null;
    static {
        setCacheDirPathDefault();
    }

    public static void setCacheDirPath(String path) {
        CACHE_DIR = new File(path, subCacheDir);
        if (false == CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

    // e.g. //data/user/0/***/cache/subtitle_cache
    // generally, it needs root permission.
    public static void setCacheDirPathDefault() {
        File defaultFile = new File(System.getProperty("java.io.tmpdir"));
        String defaultPath = defaultFile.getAbsolutePath();

        setCacheDirPath(defaultPath);
    }

    // e.g. //storage/emulated/0/Android/data/***/cache/subtitle_cache
    public static void setCacheDirPathNotDefault(String path) {
        if (true == stringIsNullOrEmpty(path)) {
            return;
        }

        setCacheDirPath(path);
    }

    /**
      * Checks if a subtitle contains duplicates,
        deduplicates it if necessary, and caches it locally.
      * @return The local file URL if deduplication and caching succeed,
                otherwise the original URL.
      */
    public static String checkAndDeduplicate(String remoteSubtitleUrl,
                                             MediaFormat format) {
        File cacheFile = getDeduplicatedCachefileName(remoteSubtitleUrl, format);

        int deduplicatedBefore = hasTheSubtitleBeenDeduplicatedBefore(cacheFile);
        // Yes, it has been deduplicated before.
        if (0 == deduplicatedBefore) {
            String cacheFilePathForExoplayer = pathUsedByExoplayer(cacheFile);
            return cacheFilePathForExoplayer;
        }

        String downloadedContent = downloadRemoteText(remoteSubtitleUrl,3,1000);
        if (null == downloadedContent) {
            return remoteSubtitleUrl;
        }

        if (false == containsDuplicatedEntries(downloadedContent)) {
            return remoteSubtitleUrl;
        }

        String finalContent = deduplicateContent(downloadedContent);

        String localSubtitleUrl = storeItToCacheDir(finalContent,
                                                    remoteSubtitleUrl,
                                                    format);
        if (null == localSubtitleUrl) {
            return remoteSubtitleUrl;
        }

        return localSubtitleUrl;
    }

    /**
     * Downloads plain text content from a remote HTTP(S) URL.
     * This method does not support local file paths or 'file://' URLs.
     *
     * @param urlStr the full HTTP or HTTPS URL to download from
     * @return the content as a String, or null if download fails
     */
    private static String downloadRemoteText(String urlStr) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new URL(urlStr).openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            System.err.println(TAG + ": Failed to download subtitle: " + e.getMessage());
            return null;
        }
    }

    private static String downloadRemoteText(String urlStr, int maxRetries, int initialDelayMillis) {
        Downloader downloader = NewPipe.getDownloader();
        if (downloader == null) {
            System.err.println(TAG + ": Downloader not initialized");
            return null;
        }
        // if auto-translate language subtitle, use the bigger data.
        int delay = initDelayValue(urlStr, initialDelayMillis);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, List<String>> headers = new HashMap<>();
                headers.put("Accept", Collections.singletonList("text/*"));
                headers.put("Accept-Language", Collections.singletonList("en-US,en;q=0.9"));
                Response response = downloader.get(urlStr, headers);
                if (response.responseCode() == 200) {
                    return response.responseBody();
                } else {
                    System.err.println(TAG + ": Attempt " + attempt + " failed with status: " + response.responseCode());
                    if (response.responseCode() != 503 && response.responseCode() != 429) {
                        return null;
                    }
                }
            } catch (IOException | ReCaptchaException e) {
                System.err.println(TAG + ": Attempt " + attempt + " failed: " + e.getMessage());
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delay);
                    delay *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        System.err.println(TAG + ": Failed to download subtitle after " + maxRetries + " attempts: " + urlStr);
        return null;
    }

    private static boolean isAutoTranslateSubtitleUrl(String urlStr) {
        if (null != checkAutoTranslateLanguage(urlStr)) {
            return true;
        } else {
            return false;
        }
    }

    private static int initDelayValue(String urlStr, int inputDelay) {
        int initDelay = 0;

        if (true == isAutoTranslateSubtitleUrl(urlStr)) {
            initDelay = 6500;
        } else {
            initDelay = inputDelay;
        }

        return initDelay;
    }

    public static boolean containsDuplicateTtmlEntries(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return false;

        try {
            String content = readFileToString(subtitleFile);
            return containsDuplicatedEntries(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean containsDuplicatedEntries(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return false;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String key = getSubtitleKeyOfTtml(matcher);

            if (seen.contains(key)) {
                return true;
            }
            seen.add(key);
        }

        return false;
    }

    private static String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static String deduplicateTtmlFile(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return "";

        try {
            String content = readFileToString(subtitleFile);
            return deduplicateContent(content);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String deduplicateContent(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return subtitleContent;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        StringBuilder result = new StringBuilder();

        int lastIndex = 0;
        while (matcher.find()) {
            result.append(subtitleContent, lastIndex, matcher.start());

            String key = getSubtitleKeyOfTtml(matcher);

            if (!seen.contains(key)) {
                result.append(matcher.group(0));
                seen.add(key);
            }

            lastIndex = matcher.end();
        }

        result.append(subtitleContent.substring(lastIndex));
        return result.toString();
    }

    private static boolean stringIsNullOrEmpty(String inputString) {
        if (null == inputString) {
            return true;
        }

        if (true == inputString.isEmpty()) {
            return true;
        }

        return false;
    }

    private static Pattern defineTtmlSubtitlePattern() {
        return Pattern.compile(
            "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
            Pattern.DOTALL
        );
    }

    private static Matcher getTtmlMatcher(String subtitleContent) {
        Pattern pattern = defineTtmlSubtitlePattern();
        return pattern.matcher(subtitleContent);
    }

    private static String getSubtitleKeyOfTtml(Matcher matcher) {
        String begin = matcher.group(1).trim();
        String end = matcher.group(2).trim();
        String content = matcher.group(3).trim().replaceAll("\\s+", " ");
        String key = begin + "|" + end + "|" + content;
        return key;
    }

    private static String pathUsedByExoplayer(File subtitleCacheFile) {
        String path = LOCAL_SUBTITLE_URL_PREFIX + subtitleCacheFile.getAbsolutePath();

        return path;
    }

    private static String storeItToCacheDir(String subtitleContent,
                                            String subtitleUrl,
                                            MediaFormat format) {
        File cacheFile = getDeduplicatedCachefileName(subtitleUrl, format);

        String cacheFilePathForExoplayer = pathUsedByExoplayer(cacheFile);

        if (false == ensureItsParentDirExist(cacheFile)) {
            return null;
        }

        if (null == writeDeduplicatedContentToCachefile(subtitleContent, cacheFile)) {
            return cacheFilePathForExoplayer;
        } else {
            System.err.println(TAG + ": Failed to write cache file: " + cacheFile.getAbsolutePath());
            return null;
        }
    }

    private static String computeShorterFilename(String subtitleUrl,
                                                MediaFormat format,
                                                String tag0) {
        String videoId = getVideoId(subtitleUrl);
        String baseName = videoId;

        //String fileExtension = "." + format;
        //String filename = baseName + fileExtension;
        String languageCode = getLanguageCode(subtitleUrl);

        StringBuilder filenameBuilder = getCommonFilename(baseName,tag0,
                                                    languageCode,format);

        String autoTranslateLanguage = checkAutoTranslateLanguage(subtitleUrl);

        if (null != autoTranslateLanguage) {
            filenameBuilder = addAutoTranslateLanguage(filenameBuilder,
                                                        autoTranslateLanguage);
        }

        String filename = filenameBuilder.toString();

        return filename;
    }

    private static StringBuilder getCommonFilename(String baseName,String tag0,
                                            String languageCode,
                                            MediaFormat format) {
        StringBuilder filenameBuilder = new StringBuilder(baseName);
        String part0_append = "-" + tag0;
        filenameBuilder.append(part0_append);

        String key = YoutubeParsingHelper.LANG;
        //for example: &lang=en
        String part1_append = "&" + key + "=" + languageCode;
        filenameBuilder.append(part1_append);

        String part2_append = "&fmt=" + format.getSuffix();
        filenameBuilder.append(part2_append);

        //the last filename is like: lUDPjyfmJrs-deduplicated&lang=en&fmt=ttml

        return filenameBuilder;
    }

    private static StringBuilder addAutoTranslateLanguage(StringBuilder filenameBuilder,
                                                    String autoTranslateLanguage) {
        String key = YoutubeParsingHelper.TLANG;
        String part0_append = "&" + key + "=" + autoTranslateLanguage;
        filenameBuilder.append(part0_append);

        return filenameBuilder;
    }

    private static String checkAutoTranslateLanguage(String subtitleUrl) {
        String language_autoTranslate = getAutoTranslateLanguage(subtitleUrl);

        if(true == stringIsNullOrEmpty(language_autoTranslate)) {
            return null;
        } else {
            return language_autoTranslate;
        }
    }

    private static String getLanguageCode(String remoteSubtitleUrl) {
        String languageCode = null;
        languageCode = YoutubeParsingHelper.extractLanguageCode(remoteSubtitleUrl);
        return languageCode;
    }

    private static String getAutoTranslateLanguage(String remoteSubtitleUrl) {
        String Auto_Translate = null;
        Auto_Translate = YoutubeParsingHelper.extractTranslationCode(remoteSubtitleUrl);
        return Auto_Translate;
    }

    // Extract the videoId (e.g., "lUDPjyfmJrs") from a subtitle URL
    // (e.g., .../api/timedtext?v=lUDPjyfmJrs)
    // for use in generating unique filenames.
    private static String getVideoId(String remoteSubtitleUrl) {
        String videoId = YoutubeParsingHelper.extractVideoId(remoteSubtitleUrl);
        return videoId;
    }

    private static File getDeduplicatedCachefileName(String subtitleUrl, MediaFormat format) {
        String tag0 = "deduplicated";

        File DeduplicatedFileName = getCachefileName(subtitleUrl,format,tag0);

        return DeduplicatedFileName;
    }

    private static File getCachefileName(String subtitleUrl,
                                        MediaFormat format,
                                        String tag0) {
        String cachefilename = computeShorterFilename(subtitleUrl, format, tag0);

        File tempCacheFile = new File(CACHE_DIR, cachefilename);

        return tempCacheFile;
    }

    // 0: it has been deduplicated bofore.
    private static int hasTheSubtitleBeenDeduplicatedBefore(File tempCacheFile) {
        if (tempCacheFile.exists()) {
            if (true == isFileEmpty(tempCacheFile)) {
                return 1; // error
            } else {
                return 0;
            }
        } else {
            return 2;
        }
    }

    private static boolean isFileEmpty(File file) {
        if(0 == file.length()) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean ensureItsParentDirExist(File tempCacheFile) {
        File parentDir = tempCacheFile.getParentFile();

        if (parentDir.exists()) {
            return true;
        } else {
            boolean success = parentDir.mkdirs();
            if (true == success) {
                return true;
            } else {
                return false;
            }
        }
    }

    private static String writeDeduplicatedContentToCachefile(
                                                String subtitleContent,
                                                File tempCacheFile) {
        String result = writeContentToFile(subtitleContent, tempCacheFile);
        return result;
    }

    private static String writeContentToFile(String content, File tempFile) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write(content);
            return null;//ok
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            System.err.println(TAG + ": Failed to write cache file: " + errorMessage);
            return errorMessage;
        }
    }

}
