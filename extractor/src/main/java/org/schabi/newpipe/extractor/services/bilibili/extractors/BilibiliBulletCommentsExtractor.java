package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.utils.decompress;

import com.google.protobuf.TextFormat;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.bilibili.ProtobufParser;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

public class BilibiliBulletCommentsExtractor extends BulletCommentsExtractor {

    public BilibiliBulletCommentsExtractor(StreamingService service, ListLinkHandler uiHandler) {
        super(service, uiHandler);
    }

    JsonObject json = new JsonObject();

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
//        String response = downloader.get(getUrl()).responseBody();
//        byte[] decompressBytes = decompress(response.getBytes());//调用解压函数进行解压，返回包含解压后数据的byte数组
//        response = new String(decompressBytes);

        try {
            ResponseBody response = downloader.get(getUrl()).rawResponseBody();
            BufferedSource source = response.source();
            source.request(Long.MAX_VALUE);
            Buffer buffer = source.buffer();
            byte[] responseBytes = buffer.clone().readByteArray();
            ProtobufParser.DmSegMobileReply reply = ProtobufParser.DmSegMobileReply.parseFrom(responseBytes);
            String a = TextFormat.printer().printToString(reply);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getInitialPage() throws IOException, ExtractionException {
        return null;
    }

    @Override
    public InfoItemsPage<BulletCommentsInfoItem> getPage(Page page) throws IOException, ExtractionException {
        return null;
    }
}
