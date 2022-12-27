package org.schabi.newpipe.extractor.services.bilibili;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Inflater;

public class utils {
    int[] s = {11, 10, 3, 8, 4, 6};
    public  int xor = 177451812;
    public long add = 8728348608L;
    public String table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF";
    public Map<Character, Integer> map =new HashMap<Character, Integer>();
    public utils(){
        for(int i=0;i<58;i++){
            map.put(table.charAt(i), i);
        }
    }
    public Long bv2av(String bv){
        long r = 0;
        for(int i=0;i<6;i++){
            r += map.get(bv.charAt(s[i]))*Math.pow(58, i);
        }
        return (r - add) ^ xor;
    }
    public String av2bv(Long x) throws ParsingException {
        String result = av2bvImpl(x, false);
        return result.contains(" ")?av2bvImpl(x, true):result;
    }
    /*
        for unknown reason, some devices resolve the index as index - 1
        flag is set to true that case
     */
    private String av2bvImpl(Long x, boolean flag) throws ParsingException {
        x = (x^xor) + add;
        String[] r = "BV1  4 1 7  ".split("");
        for(int i=0;i<6;i++){
            r[s[i] + (flag?1:0)] = String.valueOf(table.charAt((int) ((x / Math.pow(58, i)) % 58)));
        }
        String result = "";
        for(String i: r){
            result += i;
        }
        if(flag && result.contains(" ")){
            throw new ParsingException(String.format("Failed to convert av to bv. av number: %s", x));
        }
        return result;
    }
    public static String getUrl(String url, String id){
        String p = "1";
        if(url.contains("p=")){
            p = url.split("p=")[1].split("&")[0];
        }
        return "https://api.bilibili.com/x/web-interface/view?bvid="+ id+ "&p="+ p;
    }
    public static String getPureBV(String id){
        return id.split("\\?")[0];
    }
    public static String getChannelApiUrl(String url, String id){
        String pn = "1";
        if(url.contains("pn=")){
            pn = url.split("pn=")[1].split("&")[0];
        }
        return "https://api.bilibili.com/x/space/arc/search?pn="+ pn +"&ps=10&mid=" + id;
    }
    public static String getRecordApiUrl(String url){
        String pn = "1", sid, mid;
        if(url.contains("pn=")){
            pn = url.split("pn=")[1].split("&")[0];
        }
        mid = Optional.of(url.split("space.bilibili.com/")[1].split("/")[0]).orElse(url.split("space.bilibili.com/")[1]);
        sid = url.split("sid=")[1];
        return String.format("https://api.bilibili.com/x/series/archives?mid=%s&series_id=%s&only_normal=true&sort=desc&pn=%s&ps=30",mid, sid, pn);
    }
    public static String getMidFromRecordUrl(String url){
        return url.split("space.bilibili.com/")[1].split("/")[0];
    }
    public static String getMidFromRecordApiUrl(String url){
        return url.split("mid=")[1].split("&")[0];
    }
    public static byte[] decompress(byte[] data) {
        byte[] output;

        Inflater decompresser = new Inflater(true);//这个true是关键
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
    public static String bcc2srt(JsonObject bcc){
        JsonArray array = bcc.getArray("body");
        StringBuilder result = new StringBuilder();
        for(int i = 0 ; i < array.size(); i++){
            JsonObject temp = array.getObject(i);
            result.append(i+1).append("\n")
                    .append(sec2time(temp.getDouble("from")))
                    .append(" --> ")
                    .append(sec2time(temp.getDouble("to"))).append("\n")
                    .append(temp.getString("content")).append("\n\n");
        }
        return result.toString();
    }
    public static String sec2time(double sec){
        int h = (int) (sec/3600);
        int m = (int) (sec/60%60);
        int s = (int) (sec % 60);
        int f = (int) ((sec * 1000) % 1000);
        return String.format("%02d:%02d:%02d,%03d", h, m, s, f);
    }
}
