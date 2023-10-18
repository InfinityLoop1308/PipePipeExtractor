package org.schabi.newpipe.extractor.services.bilibili.linkHandler;

import java.io.IOException;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.bilibili.utils;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.LIVE_BASE_URL;

/*
General form of stream link url: https://m.bilibili.com/video/<ID> (mobile) and https://www.bilibili.com/video/<ID> (PC)
*/
public class BilibiliStreamLinkHandlerFactory extends LinkHandlerFactory{

    public static final String baseUrl = "https://www.bilibili.com/video/";
    public String p = "1";
    private static final Downloader downloader = NewPipe.getDownloader();

    @Override
    public String getId(String url) throws ParsingException {
        if(url.contains("b23.tv")){
            try {
                url = downloader.get("https://b23.wtf/api?full=" + url.split("://")[1] +"&status=200").responseBody().trim();
            } catch (IOException | ReCaptchaException e) {
                throw new RuntimeException(e);
            }
        }
        if(url.contains("p=")){
            p = url.split("p=")[1].split("&")[0];
        }
        if(url.endsWith("/")){
            url = url.substring(0, url.length() - 1);
        }
        if(url.contains("/?")){
            url = url.replace("/?", "?");
        }
        if (url.split("/")[url.split("/").length-1].startsWith("BV")) {
            String parseResult = url.split(Pattern.quote("/BV"))[1].split("\\?")[0].split("/")[0];
            return "BV"+parseResult + "?p="+p;
        } else if (url.contains("bvid=")) {
            String  parseResult = url.split(Pattern.quote("bvid="))[1].split("&")[0];
            return parseResult+ "?p="+p;
        } else if (url.split("/")[url.split("/").length-1].startsWith("av")) {
            String  parseResult = url.split(Pattern.quote("av"))[1].split("\\?")[0];
            return new utils().av2bv(Long.parseLong(parseResult))+ "?p="+p;
        }else if (url.contains("aid=")) {
            String  parseResult = url.split(Pattern.quote("aid="))[1].split("&")[0];
            return new utils().av2bv(Long.parseLong(parseResult))+ "?p="+p;
        } else if(url.contains(LIVE_BASE_URL) || url.contains("bangumi/play/")){
            return url.split("/")[url.split("/").length-1].split("\\?")[0];
        } else{
            throw new ParsingException("Not a bilibili video link.");
        }
    }

    @Override
    public String getUrl(final String id) {
        if(id.startsWith("BV")) {
            return "https://bilibili.com/"+id;
        } else if (id.startsWith("ss") || id.startsWith("ep")) {
            return "https://www.bilibili.com/bangumi/play/" + id;
        } else return "https://live.bilibili.com/"+id;
    }

    @Override
    public boolean onAcceptUrl(final String url) throws ParsingException {
        try {
            if(url.contains("b23.tv")){
                return true;
            }
            getId(url);
            return true;
        } catch (ParsingException e) {
            return false;
        }
    }

}
