import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.Image;
import sx.blah.discord.util.RateLimitException;

/**
 * Created by Trent on 12/21/2016.
 */
public class Tuturu {
    private static final String token = "***REMOVED***";

    public static IDiscordClient client;

    public static void main(String[] args) throws DiscordException, RateLimitException {
        client = new ClientBuilder().withToken(token).build();
        client.getDispatcher().registerListener(new TuturuListener(client));
        client.login();
    }
}
