package com.trentwdb.mayushii;

import com.google.gson.Gson;
import com.trentwdb.mayushii.model.ImageObject;
import com.trentwdb.mayushii.model.SearchResult;
import com.trentwdb.mayushii.model.SearchResults;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by mlaux on 12/23/16.
 */
public class ImageSearchListener {
    private static final String PREFIX = "/img";
    private static final String URL_TEMPLATE = "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s";

    private static final OkHttpClient OKHTTP = new OkHttpClient();
    private static final Gson GSON = new Gson();

    private final String googleKey;
    private final String searchEngineId;

    public ImageSearchListener(String googleKey, String searchEngineId) {
        this.googleKey = googleKey;
        this.searchEngineId = searchEngineId;
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) {
        IMessage discordMessage = event.getMessage();
        IUser user = discordMessage.getAuthor();
        if (user.isBot()) {
            return;
        }

        IChannel channel = discordMessage.getChannel();
        String message = discordMessage.getContent().toLowerCase().trim()
                .replaceAll("\\s+", " ");
        String[] split = message.split(" ", 2);

        if (split.length < 2 || !split[0].equals(PREFIX)) {
            return;
        }

        String query;
        try {
            query = URLEncoder.encode(split[1], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("No UTF-8 wtf");
        }

        String url = String.format(URL_TEMPLATE, googleKey, searchEngineId, query);
        Request request = new Request.Builder()
                .url(url)
                .build();

        try {
            Response response = OKHTTP.newCall(request).execute();
            SearchResults results = GSON.fromJson(response.body().string(), SearchResults.class);
            for (SearchResult result : results.items) {
                if (result.pagemap.imageobject.size() > 0) {
                    ImageObject obj = result.pagemap.imageobject.get(0);
                    if (obj.url != null) {
                        channel.sendMessage(obj.url);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Discord4J.LOGGER.error("Error getting image result", e);
        }
    }
}