import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.TrackFinishEvent;
import sx.blah.discord.util.audio.events.TrackQueueEvent;
import sx.blah.discord.util.audio.events.TrackStartEvent;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Trent on 12/21/2016.
 */
public class TuturuListener {
    private static final String PREFIX = "?";
    // this value is supposed to be channel.getUserLimit() but it's returning 0
    private static final int CHANNEL_LIMIT = 1000;
    private final Map<IGuild, IChannel> lastChannel = new HashMap<IGuild, IChannel>();
    private IDiscordClient client;


    public TuturuListener(IDiscordClient client) {
        this.client = client;
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        System.out.println("Tuturu bot is now ready!");
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IMessage discordMessage = event.getMessage();
        IUser user = discordMessage.getAuthor();
        if (user.isBot()) {
            return;
        }

        IChannel channel = discordMessage.getChannel();
        IGuild guild = discordMessage.getGuild();
        String message = discordMessage.getContent().toLowerCase().replaceAll("\\s+", " ");
        String[] split = message.split(" ");
        System.out.println("Message: " + message);

        if (split.length < 1 || !split[0].startsWith(PREFIX)) {
            return;
        }

        lastChannel.put(guild, channel);

        String command = split[0].substring(1);
        String[] args = split.length >= 2 ? Arrays.copyOfRange(split, 1, split.length) : new String[0];

        if (command.equalsIgnoreCase("join")) {
            join(channel, user);
        } else if (command.equalsIgnoreCase("queue")) {
            String address = String.join(" ", args);

            if (new File(address).exists()) {
                // file
                queueFile(channel, address);
                System.out.println("Queueing file");
            } else {
                // url
                queueUrl(channel, address);
            }
        } else if (command.equalsIgnoreCase("play") || command.equalsIgnoreCase("unpause")) {
            pause(channel, false);
        } else if (command.equalsIgnoreCase("pause")) {
            if (getPlayer(guild).isPaused()) pause(channel, false);
            else pause(channel, true);
        } else if (command.equalsIgnoreCase("skip")) {
            skip(channel);
        } else if (command.equalsIgnoreCase("vol") || command.equalsIgnoreCase("volume")) {
            try {
                volume(channel, Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                channel.sendMessage("Invalid volume percentage.");
            }
        }
    }

	/*
	Track events
	 */

    @EventSubscriber
    public void onTrackQueue(TrackQueueEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        System.out.println(lastChannel.get(guild).getName());
        lastChannel.get(guild).sendMessage("Added **" + getTrackTitle(event.getTrack()) + "** to the playlist.");
    }

    @EventSubscriber
    public void onTrackStart(TrackStartEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        lastChannel.get(guild).sendMessage("Now playing **" + getTrackTitle(event.getTrack()) + "**.");
    }

    @EventSubscriber
    public void onTrackFinish(TrackFinishEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        lastChannel.get(guild).sendMessage("Finished playing **" + getTrackTitle(event.getOldTrack()) + "**.");

        if (event.getNewTrack() == null)
            lastChannel.get(guild).sendMessage("The playlist is now empty.");
    }

	/*
	Audio player methods
	 */

    private void join(IChannel channel, IUser user) throws RateLimitException, DiscordException, MissingPermissionsException {
        if (user.getConnectedVoiceChannels().size() < 1) {
            channel.sendMessage("You aren't in a voice channel!");
        } else {
            IVoiceChannel voice = user.getConnectedVoiceChannels().get(0);
            if (!voice.getModifiedPermissions(client.getOurUser()).contains(Permissions.VOICE_CONNECT)) {
                channel.sendMessage("I can't join that voice channel!");
            } else if (voice.getConnectedUsers().size() >= CHANNEL_LIMIT) {
                channel.sendMessage("That room is full!");
            } else {
                voice.join();
                channel.sendMessage("Connected to **" + voice.getName() + "**.");
            }
        }
    }

    private void queueUrl(IChannel channel, String url) throws RateLimitException, DiscordException, MissingPermissionsException {
        try {
            URL u = new URL(url);
            setTrackTitle(getPlayer(channel.getGuild()).queue(u), u.getFile());
            System.out.println("Queued file " + url);
        } catch (MalformedURLException e) {
            channel.sendMessage("That URL is invalid!");
        } catch (IOException e) {
            channel.sendMessage("An IO exception occured: " + e.getMessage());
        } catch (UnsupportedAudioFileException e) {
            channel.sendMessage("That type of file is not supported!");
        }
    }

    private void queueFile(IChannel channel, String file) throws RateLimitException, DiscordException, MissingPermissionsException {
        File f = new File(file);
        if (!f.exists())
            channel.sendMessage("That file doesn't exist!");
        else if (!f.canRead())
            channel.sendMessage("I don't have access to that file!");
        else {
            try {
                setTrackTitle(getPlayer(channel.getGuild()).queue(f), f.toString());
            } catch (IOException e) {
                channel.sendMessage("An IO exception occured: " + e.getMessage());
            } catch (UnsupportedAudioFileException e) {
                channel.sendMessage("That type of file is not supported!");
            }
        }
    }

    private void pause(IChannel channel, boolean pause) {
        getPlayer(channel.getGuild()).setPaused(pause);
    }

    private void skip(IChannel channel) {
        getPlayer(channel.getGuild()).skip();
    }

    private void volume(IChannel channel, int percent) throws RateLimitException, DiscordException, MissingPermissionsException {
        volume(channel, (float) (percent) / 100);
    }

    private void volume(IChannel channel, Float vol) throws RateLimitException, DiscordException, MissingPermissionsException {
        if (vol > 1.5) {
            vol = 1.5f;
        }
        if (vol < 0) {
            vol = 0f;
        }
        getPlayer(channel.getGuild()).setVolume(vol);
        channel.sendMessage("Set volume to **" + (int) (vol * 100) + "%**.");
    }

	/*
	Utility methods
	 */

    private AudioPlayer getPlayer(IGuild guild) {
        return AudioPlayer.getAudioPlayerForGuild(guild);
    }

    private String getTrackTitle(AudioPlayer.Track track) {
        return track.getMetadata().containsKey("title") ? String.valueOf(track.getMetadata().get("title")) : "Unknown Track";
    }

    private void setTrackTitle(AudioPlayer.Track track, String title) {
        track.getMetadata().put("title", title);
    }
}
