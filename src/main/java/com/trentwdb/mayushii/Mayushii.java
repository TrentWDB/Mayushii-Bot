package com.trentwdb.mayushii;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.RateLimitException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by Trent on 12/21/2016.
 */
public class Mayushii {
    private static final String PROPERTY_DISCORD_TOKEN = "discord_token";
    private static final String PROPERTY_DISCORD_USERNAME = "discord_username";
    private static final String PROPERTY_GOOGLE_API_KEY = "google_api_key";
    private static final String PROPERTY_GOOGLE_SEARCH_ENGINE_ID = "google_search_engine_id";

    private static Properties properties;

    public static IDiscordClient client;

    public static void main(String[] args) throws DiscordException, RateLimitException, IOException {
        loadConfig();

        String token = properties.getProperty(PROPERTY_DISCORD_TOKEN);
        String googleKey = properties.getProperty(PROPERTY_GOOGLE_API_KEY);
        String searchEngineId = properties.getProperty(PROPERTY_GOOGLE_SEARCH_ENGINE_ID);

        client = new ClientBuilder().withToken(token).build();

        EventDispatcher disp = client.getDispatcher();
        disp.registerListener(new TuturuListener(client, args));
        disp.registerListener(new ImageSearchListener(googleKey, searchEngineId));

        client.login();

        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            // no-op
        }

        client.changeUsername(properties.getProperty(PROPERTY_DISCORD_USERNAME));
    }

    private static void loadConfig() throws IOException {
        properties = new Properties();
        properties.load(new FileInputStream("mayushii.properties"));
    }
}
