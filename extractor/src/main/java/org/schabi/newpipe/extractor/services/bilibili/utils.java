package org.schabi.newpipe.extractor.services.bilibili;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.brotli.dec.BrotliInputStream;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Inflater;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.*;


public class utils {
    int[] s = {11, 10, 3, 8, 4, 6};
    public int xor = 177451812;
    public long add = 8728348608L;
    public String table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF";
    public Map<Character, Integer> map = new HashMap<Character, Integer>();
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String REFERER = "https://www.bilibili.com";
    private static final OkHttpClient client = new OkHttpClient();


    public utils() {
        for (int i = 0; i < 58; i++) {
            map.put(table.charAt(i), i);
        }
    }

    public Long bv2av(String bv) {
        long r = 0;
        for (int i = 0; i < 6; i++) {
            r += map.get(bv.charAt(s[i])) * Math.pow(58, i);
        }
        return (r - add) ^ xor;
    }

    public String av2bv(Long x) throws ParsingException {
        String result = av2bvImpl(x, false);
        return result.contains(" ") ? av2bvImpl(x, true) : result;
    }

    /*
        for unknown reason, some devices resolve the index as index - 1
        flag is set to true that case
     */
    private String av2bvImpl(Long x, boolean flag) throws ParsingException {
        x = (x ^ xor) + add;
        String[] r = "BV1  4 1 7  ".split("");
        for (int i = 0; i < 6; i++) {
            r[s[i] + (flag ? 1 : 0)] = String.valueOf(table.charAt((int) ((x / Math.pow(58, i)) % 58)));
        }
        StringBuilder result = new StringBuilder();
        for (String i : r) {
            result.append(i);
        }
        if (flag && result.toString().contains(" ")) {
            throw new ParsingException(String.format("Failed to convert av to bv. av number: %s", x));
        }
        return result.toString();
    }

    public static String getUrl(String url, String id) {
        String p = "1";
        if (url.contains("p=")) {
            p = url.split("p=")[1].split("&")[0];
        }
        return "https://api.bilibili.com/x/web-interface/view?bvid=" + id + "&p=" + p;
    }

    public static String getPureBV(String id) {
        return id.split("\\?")[0];
    }

    public static String getUserVideos(String url, String id, Downloader downloader) throws IOException, ReCaptchaException, ParsingException, JsonParserException {
        String pn = "1";
        if (url.contains("pn=")) {
            pn = url.split("pn=")[1].split("&")[0];
        }
        Map<String, String> params = new HashMap<>();
        params.put("pn", pn);
        params.put("ps", "30");
        params.put("mid", id);
        params.put("platform", "web");
        String[] result = encWbi(params);
        params.put("w_rid", result[0]);
        params.put("wts", result[1]);
        //get new url
        String newUrl = QUERY_USER_VIDEOS_URL + "?" + params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return downloader.get(newUrl, getUpToDateHeaders()).responseBody();
    }

    public static String getRecordApiUrl(String url) {
        String pn = "1", sid, mid;
        if (url.contains("pn=")) {
            pn = url.split("pn=")[1].split("&")[0];
        }
        mid = Optional.of(url.split("space.bilibili.com/")[1].split("/")[0]).orElse(url.split("space.bilibili.com/")[1]);
        sid = url.split("sid=")[1];
        return String.format("https://api.bilibili.com/x/series/archives?mid=%s&series_id=%s&only_normal=true&sort=desc&pn=%s&ps=30", mid, sid, pn);
    }

    public static String getMidFromRecordUrl(String url) {
        return url.split("space.bilibili.com/")[1].split("/")[0];
    }

    public static String getMidFromRecordApiUrl(String url) {
        return url.split("mid=")[1].split("&")[0];
    }

    public static String bcc2srt(JsonObject bcc) {
        JsonArray array = bcc.getArray("body");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            JsonObject temp = array.getObject(i);
            result.append(i + 1).append("\n")
                    .append(sec2time(temp.getDouble("from")))
                    .append(" --> ")
                    .append(sec2time(temp.getDouble("to"))).append("\n")
                    .append(temp.getString("content")).append("\n\n");
        }
        return result.toString();
    }

    public static String sec2time(double sec) {
        int h = (int) (sec / 3600);
        int m = (int) (sec / 60 % 60);
        int s = (int) (sec % 60);
        int f = (int) ((sec * 1000) % 1000);
        return String.format("%02d:%02d:%02d,%03d", h, m, s, f);
    }

    public static byte[] decompress(byte[] data) throws IOException {
        byte[] decompressData = null;
        Inflater decompressor = new Inflater(true);
        decompressor.reset();
        decompressor.setInput(data);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            byte[] buf = new byte[1024];
            while (!decompressor.finished()) {
                int i = decompressor.inflate(buf);
                outputStream.write(buf, 0, i);
            }
            decompressData = outputStream.toByteArray();
        } catch (Exception ignored) {
        }
        decompressor.end();
        return decompressData;
    }

    public static byte[] decompressZlib(byte[] data) {
        byte[] output = new byte[0];

        Inflater decompresser = new Inflater();
        decompresser.reset();
        decompresser.setInput(data);

        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[1024];
            while (!decompresser.finished()) {
                int i = decompresser.inflate(buf);
                o.write(buf, 0, i);
            }
            output = o.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                o.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        decompresser.end();
        return output;
    }

    public static String decompressBrotli(byte[] body) throws IOException {
        try {
            return new BufferedReader(new InputStreamReader(new BrotliInputStream(
                    new ByteArrayInputStream(body)))).lines().collect(Collectors.joining());
        } catch (Exception e) {
            // if SDK <= 24
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            InputStream is = new BrotliInputStream(bais);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    //Get next page's url from current page
    public static String getNextPageFromCurrentUrl(String currentUrl, final String varName, final int addCount
            , final boolean shouldTryInit, final String initValue, final String urlType)
            throws ParsingException {
        String varString = String.format("&%s=", varName);
        String varStringVariant = String.format("?%s=", varName);
        if (shouldTryInit && !currentUrl.contains(varString) && !currentUrl.contains(varStringVariant)) {
            varString = varString.replace("&", urlType);
            currentUrl += varString + initValue;
        }
        if(currentUrl.contains(varStringVariant)) {
            varString = varStringVariant;
        } else if(!currentUrl.contains(varString)) {
            throw new ParsingException("Could not find " + varName + " in url: " + currentUrl);
        }

        final String offset = currentUrl.split(Pattern.quote(varString))[1].split(Pattern.quote("&"))[0];
        return currentUrl.replace(varString + offset, varString
                + (Integer.parseInt(offset) + addCount));
    }

    // Default value of getNextPageFromCurrentUrl, shouldTryInit = false, initValue = 1, urlType = &
    public static String getNextPageFromCurrentUrl(String currentUrl, final String varName, final int addCount)
            throws ParsingException {
        return getNextPageFromCurrentUrl(currentUrl, varName, addCount, false, "0", "&");
    }

    public static long getDurationFromString(String duration) {
        long result = 0;
        int len = duration.split(":").length;
        result += Integer.parseInt(duration.split(":")[len-1]);
        if(len > 1) {
            result += Integer.parseInt(duration.split(":")[len-2]) * 60L;
        }
        if(len > 2) {
            result += (long) Integer.parseInt(duration.split(":")[len - 3]) * 60 * 60;
        }
        return result;
    }
    private static String getMixinKey(String ae) {
        int[] oe = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41,
                13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52};
        String le = Arrays.stream(oe)
                .mapToObj(i -> String.valueOf((char) ae.charAt(i)))
                .collect(Collectors.joining());
        return le.substring(0, 32);
    }

    public static String[] encWbi(Map<String, String> params) {
        String wbiImgUrl = WBI_IMG_URL;
        String img_value;
        String sub_value;
        String img_url;
        String sub_url;
        String me;
        int wts;
        String w_rid;

        try {
            Request request = new Request.Builder()
                    .url(wbiImgUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", REFERER)
                    .build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to get wbi_img");
            }
            String responseBody = response.body().string();
            img_url = responseBody.split("\"img_url\":\"")[1].split("\"")[0];
            sub_url = responseBody.split("\"sub_url\":\"")[1].split("\"")[0];
            img_value = img_url.split("/")[img_url.split("/").length - 1].split("\\.")[0];
            sub_value = sub_url.split("/")[sub_url.split("/").length - 1].split("\\.")[0];
            me = getMixinKey(img_value + sub_value);
            wts = (int) (System.currentTimeMillis() / 1000);
            params.put("wts", String.valueOf(wts));
            Map<String, String> sortedParams = new TreeMap<>(params);
            String ae = sortedParams.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            String toHash = ae + me;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(toHash.getBytes());
            w_rid = bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new String[]{w_rid, String.valueOf(wts)};
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static public void checkResponse(String response) throws ParsingException, JsonParserException {
        JsonObject responseJson = JsonParser.object().from(response);
        if (responseJson.getLong("code") != 0) {
            throw new ParsingException("Failed request, response: " + response);
        }
    }

    static public String getUserAgentRandomly() {
        ArrayList<String> userAgents = new ArrayList<>();
        userAgents.add("Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.101 Mobile Safari/537.36");
        userAgents.add("Mozilla/5.0 (Linux; Android 7.1.1; OPPO R9sk) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.111 Mobile Safari/537.36");
        userAgents.add("Mozilla/5.0 (Linux; Android 11; Samsung SM-A025G) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19");
        userAgents.add("Mozilla/5.0 (Linux; Android 7.0; SM-G930VC Build/NRD90M; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/58.0.3029.83 Mobile Safari/537.36");
        userAgents.add("Mozilla/5.0 (Linux; Android 11; V2108) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36");
        userAgents.add("Mozilla/5.0 (Linux; Android 11; moto g(50) Build/RRFS31.Q1-59-76-2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/92.0.4515.159 Mobile Safari/537.36 EdgW/1.0");
        userAgents.add("Mozilla/5.0 (Linux; Android 11; M2102K1G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Mobile Safari/537.36");
        userAgents.add("Mozilla/5.0 (iPhone; CPU iPhone OS 14_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/92.0.4515.90 Mobile/15E148 Safari/604.1");
        userAgents.add("Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/36.0  Mobile/15E148 Safari/605.1.15");
        userAgents.add("Mozilla/5.0 (iPhone12,8; U; CPU iPhone OS 13_0 like Mac OS X) AppleWebKit/602.1.50 (KHTML, like Gecko) Version/10.0 Mobile/14A403 Safari/602.1");
        return userAgents.get(new Random().nextInt(userAgents.size()));
    }
}
