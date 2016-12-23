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
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Trent on 12/21/2016.
 */
public class TuturuListener {
    private static final String PREFIX = "?";
    private static final String[] AUDIO_EXTENSIONS = new String[]{".wav", ".mp3", ""};
    private static final boolean DEBUG = true;

    // this value is supposed to be channel.getUserLimit() but it's returning 0
    private static final int CHANNEL_LIMIT = 1000;
    private final Map<IGuild, IChannel> lastChannel = new HashMap<IGuild, IChannel>();
    private IDiscordClient client;

    private final List<String> audioPaths = new ArrayList<String>();

    public TuturuListener(IDiscordClient client, String[] audioPaths) {
        this.client = client;
        this.audioPaths.add("");
        for (String path : audioPaths) {
            path = path.trim();
            String lastCharacter = path.substring(path.length() - 1);
            if (!lastCharacter.equals("/") && !lastCharacter.equals("\\")) {
                path += "/";
            }

            this.audioPaths.add(path);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.out.println("Bye bye!");
                sendMessageToPrimaryChannel("Bye bye!");
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        System.out.println("Tuturu bot is now ready!");
        sendMessageToPrimaryChannel("Tuturu!");
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IMessage discordMessage = event.getMessage();
        IUser user = discordMessage.getAuthor();
        if (user.isBot()) {
            return;
        }

        IChannel channel = discordMessage.getChannel();
        IVoiceChannel voiceChannel = user.getConnectedVoiceChannels().size() == 0 ? null : user.getConnectedVoiceChannels().get(0);
        IGuild guild = discordMessage.getGuild();
        String message = discordMessage.getContent().toLowerCase().trim().replaceAll("\\s+", " ");
        String[] split = message.split(" ");

        if (split.length < 1 || !split[0].startsWith(PREFIX)) {
            return;
        }

        lastChannel.put(guild, channel);

        String command = split[0].substring(1);
        String[] commandParts = split.length >= 2 ? Arrays.copyOfRange(split, 1, split.length) : new String[0];
        String args = String.join(" ", commandParts);

        switch (command) {
            case "join": {
                join(channel, user);

                break;
            }

            case "queue": {
                queue(channel, args);

                break;
            }

            case "tuturu": {
                if (tuturu(channel, args) && !inRequestedChannel(voiceChannel)) {
                    join(channel, user);
                }

                break;
            }

            case "play": {
                if (queue(channel, args) && !inRequestedChannel(voiceChannel)) {
                    join(channel, user);
                }

                break;
            }

            case "pause": {
                pause(channel, true);

                break;
            }

            case "unpause": {
                pause(channel, true);

                break;
            }

            case "skip": {
                skip(channel);

                break;
            }

            case "volume": {
                try {
                    volume(channel, Integer.parseInt(commandParts[0]));
                } catch (NumberFormatException e) {
                    sendMessage(channel, "Invalid volume percentage.");
                }

                break;
            }

            default: {
                // use the command as the argument

                if (queue(channel, command)) {
                    if (!inRequestedChannel(voiceChannel)) {
                        join(channel, user);
                    }

                    break;
                }
            }
        }
    }

    private boolean queue(IChannel channel, String args) throws RateLimitException, DiscordException, MissingPermissionsException {
        if (args.startsWith("http") || args.startsWith("https") || args.startsWith("www")) {
            // url
            try {
                queueUrl(channel, args);
                return true;
            } catch (IOException | UnsupportedAudioFileException e) {
                return false;
            }
        } else {
            // file
            for (String path : audioPaths) {
                for (String extension : AUDIO_EXTENSIONS) {
                    String address = path + args + extension;

                    if (new File(address).exists()) {
                        try {
                            queueFile(channel, address);
                            System.out.println("queued file");
                            return true;
                        } catch (IOException e) {
                            System.out.println("error 1");
                            return false;
                        } catch (UnsupportedAudioFileException e) {
                            System.out.println("error 2");
                            return false;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean tuturu(IChannel channel, String args) throws RateLimitException, DiscordException, MissingPermissionsException {
        for (String extension : AUDIO_EXTENSIONS) {
            String address = "tuturus/tuturu" + (args.length() > 0 ? "-" : "") + args + extension;

            if (new File(address).exists()) {
                try {
                    queueFile(channel, address);
                    return true;
                } catch (IOException e) {
                    return false;
                } catch (UnsupportedAudioFileException e) {
                    return false;
                }
            }
        }

        return false;
    }

	/*
	Track events
	 */

    @EventSubscriber
    public void onTrackQueue(TrackQueueEvent event) throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
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
        // not in any channels, need to join one
        if (client.getOurUser().getConnectedVoiceChannels().size() == 0) {
            if (user.getConnectedVoiceChannels().size() < 1) {
                sendMessage(channel, "You aren't in a voice channel!");
            } else {
                IVoiceChannel voice = user.getConnectedVoiceChannels().get(0);
                if (!voice.getModifiedPermissions(client.getOurUser()).contains(Permissions.VOICE_CONNECT)) {
                    sendMessage(channel, "I can't join that voice channel!");
                } else if (voice.getConnectedUsers().size() >= CHANNEL_LIMIT) {
                    sendMessage(channel, "That channel is full!");
                } else if (voice.getConnectedUsers().contains(client.getOurUser())) {
                    sendMessage(channel, "I'm already in that channel!");
                } else {
                    voice.join();
                    sendMessage(channel, "Connected to **" + voice.getName() + "**.");
                }
            }
        } else {
            // already in a channel so can join a new one or stay
            if (user.getConnectedVoiceChannels().size() == 0) {
                // don't do anything, stay in current channel
                return;
            }

            IVoiceChannel voice = user.getConnectedVoiceChannels().get(0);
            voice.join();
            sendMessage(channel, "Connected to **" + voice.getName() + "**.");
        }
    }

    private boolean inRequestedChannel(IVoiceChannel channel) {
        if (channel == null) {
            return false;
        }

        return channel.getUsersHere().contains(client.getOurUser());
    }

    private void queueUrl(IChannel channel, String url) throws RateLimitException, DiscordException, MissingPermissionsException, IOException, UnsupportedAudioFileException {
        try {
            URL u = new URL(url);
            setTrackTitle(getPlayer(channel.getGuild()).queue(u), u.getFile());
        } catch (MalformedURLException e) {
            sendMessage(channel, "That URL is invalid!");
            throw e;
        } catch (IOException e) {
            sendMessage(channel, "An IO exception occured: " + e.getMessage());
            throw e;
        } catch (UnsupportedAudioFileException e) {
            sendMessage(channel, "That type of file is not supported!");
            throw e;
        }
    }

    private void queueFile(IChannel channel, String file) throws RateLimitException, DiscordException, MissingPermissionsException, IOException, UnsupportedAudioFileException {
        File f = new File(file);
        String absoluteFilePath = f.getCanonicalPath().toLowerCase();

        boolean foundParentPath = false;
        for (String path : audioPaths) {
            String absoluteValidPath = new File(path).getCanonicalPath().toLowerCase();

            if (absoluteFilePath.startsWith(absoluteValidPath)) {
                foundParentPath = true;
                break;
            }
        }

        if (!foundParentPath) {
            sendMessage(channel, "That file location is out of the allowed scope!");
            throw new IOException();
        }

        if (!f.exists()) {
            sendMessage(channel, "That file doesn't exist!");
            throw new IOException();
        }

        if (!f.canRead()) {
            sendMessage(channel, "I don't have access to that file!");
            throw new IOException();
        }

        try {
            setTrackTitle(getPlayer(channel.getGuild()).queue(f), f.toString());
        } catch (IOException e) {
            sendMessage(channel, "An IO exception occured: " + e.getMessage());
            throw e;
        } catch (UnsupportedAudioFileException e) {
            sendMessage(channel, "That type of file is not supported!");
            throw e;
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
        sendMessage(channel, "Set volume to **" + (int) (vol * 100) + "%**.");
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

    private void sendMessage(IChannel channel, String message) {
        if (!DEBUG) {
            return;
        }

        try {
            channel.sendMessage(message);
        } catch (MissingPermissionsException | RateLimitException | DiscordException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToPrimaryChannel(String message) {
        // try to send to general
        for (IChannel channel : client.getChannels()) {
            if (channel.getName().toLowerCase().equals("general")) {
                try {
                    channel.sendMessage(message);
                    return;
                } catch (MissingPermissionsException | RateLimitException | DiscordException e) {
                    e.printStackTrace();
                }
            }
        }

        // send to any willing channel tbh fam
        for (IChannel channel : client.getChannels()) {
            try {
                channel.sendMessage(message);
                return;
            } catch (MissingPermissionsException | RateLimitException | DiscordException e) {
                e.printStackTrace();
            }
        }
    }
}
