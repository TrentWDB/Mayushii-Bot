import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.Image;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Trent on 12/21/2016.
 */
public class Tuturu {
    private static String token = "";

    public static IDiscordClient client;

    public static void main(String[] args) throws DiscordException, RateLimitException, IOException {
        loadSecret();

        client = new ClientBuilder().withToken(token).build();
        client.getDispatcher().registerListener(new TuturuListener(client, args));
        client.login();
    }

    private static void loadSecret() throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get("secret.txt"));
        token = new String(encoded, Charset.defaultCharset());
    }
}
