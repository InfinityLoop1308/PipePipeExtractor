package org.schabi.newpipe.extractor.services.bilibili.extractors;

import static org.schabi.newpipe.extractor.services.bilibili.BilibiliService.getHeaders;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BilibiliSuggestionExtractor extends SuggestionExtractor {
    public BilibiliSuggestionExtractor(StreamingService service) {
        super(service);
    }

    @Override
    public List<String> suggestionList(String query) throws IOException, ExtractionException {
        final String response = NewPipe.getDownloader().get("https://s.search.bilibili.com/main/suggest?term=" + query, getHeaders()).responseBody();
        List<String> resultList = new ArrayList<>();
        try {
            JsonObject respObject = JsonParser.object().from(response);
            for(int i = 0; i < respObject.size(); i++){
                resultList.add(respObject.getObject(""+i).getString("value"));
            }
        } catch (JsonParserException e) {
            e.printStackTrace();
        }
        return resultList;
    }
}
