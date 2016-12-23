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
    }

    @EventSubscriber
    public void onReady(ReadyEvent event) {
        System.out.println("Tuturu bot is now ready!");
        // sendMessageToPrimaryChannel("Tuturu!");
    }

    @EventSubscriber
    public void onMessage(MessageReceivedEvent event) throws RateLimitException, DiscordException, MissingPermissionsException, IOException {
        IMessage discordMessage = event.getMessage();
        IUser user = discordMessage.getAuthor();
        if (user.isBot()) {
            return;
        }

        IChannel channel = discordMessage.getChannel();
        IVoiceChannel voiceChannel = user.getConnectedVoiceChannels().size() == 0 ? null : user.getConnectedVoiceChannels().get(0);
        IGuild guild = discordMessage.getGuild();
        if (guild == null) {
            // if it's a private message then get the users guild
            guild = getGuild(channel);
        }

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

            case "bye": {
                if (hasAdministratorPermissions(user, guild)) {
                    quit();
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

    private boolean queue(IChannel channel, String args) throws RateLimitException, DiscordException, MissingPermissionsException, IOException {
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
                    File file = new File(address);
                    if (!validScope(file, channel)) {
                        return false;
                    }

                    if (file.exists()) {
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
            }
        }

        return false;
    }

    private boolean tuturu(IChannel channel, String args) throws RateLimitException, DiscordException, MissingPermissionsException, IOException {
        for (String extension : AUDIO_EXTENSIONS) {
            String address = "tuturus/tuturu" + (args.length() > 0 ? "-" : "") + args + extension;
            File file = new File(address);
            if (!validScope(file, channel)) {
                return false;
            }

            if (file.exists()) {
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
            setTrackTitle(getPlayer(getGuild(channel)).queue(u), u.getFile());
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

        if (!f.exists()) {
            sendMessage(channel, "That file doesn't exist!");
            throw new IOException();
        }

        if (!f.canRead()) {
            sendMessage(channel, "I don't have access to that file!");
            throw new IOException();
        }

        try {
            setTrackTitle(getPlayer(getGuild(channel)).queue(f), f.toString());
        } catch (IOException e) {
            sendMessage(channel, "An IO exception occured: " + e.getMessage());
            throw e;
        } catch (UnsupportedAudioFileException e) {
            sendMessage(channel, "That type of file is not supported!");
            throw e;
        }
    }

    private void pause(IChannel channel, boolean pause) {
        getPlayer(getGuild(channel)).setPaused(pause);
    }

    private void skip(IChannel channel) {
        getPlayer(getGuild(channel)).skip();
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
        getPlayer(getGuild(channel)).setVolume(vol);
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
        for (IGuild guild : client.getGuilds()) {
            // try to send to general
            for (IChannel channel : guild.getChannels()) {
                if (channel.getName().toLowerCase().equals("general")) {
                    try {
                        channel.sendMessage(message);

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

    private IGuild getGuild(IChannel channel) {
        IGuild returnGuild = channel.getGuild();
        if (returnGuild != null) {
            return returnGuild;
        }

        if (channel.getUsersHere().size() > 1) {
            return null;
        }

        // we can assume it's a private message to the bot
        // first check voice channels
        IUser user = channel.getUsersHere().get(0);
        for (IGuild currentGuild : client.getGuilds()) {
            for (IVoiceChannel currentVoiceChannel : currentGuild.getVoiceChannels()) {
                if (currentVoiceChannel.getUsersHere().contains(user)) {
                    return currentGuild;
                }
            }
        }

        // now we just check guilds, hopefully the user only exists in one
        int foundCount = 0;
        IGuild foundGuild = null;
        for (IGuild currentGuild : client.getGuilds()) {
            if (currentGuild.getUsers().contains(user)) {
                foundCount++;
                foundGuild = currentGuild;
            }
        }
        if (foundCount == 1) {
            return foundGuild;
        }

        // now we give up
        return null;
    }

    private boolean hasAdministratorPermissions(IUser user, IGuild guild) {
        if (guild.getOwner().equals(user)) {
            return true;
        }

        if (user.getPermissionsForGuild(guild).contains(Permissions.ADMINISTRATOR)) {
            return true;
        }

        // TODO I'm not sure it ever goes past user.getPermissionsForGuild but I haven't checked yet
        List<IRole> roleList = user.getRolesForGuild(guild);
        for (IRole role : roleList) {
            if (role.getPermissions().contains(Permissions.ADMINISTRATOR)) {
                return true;
            }
        }

        return false;
    }

    private boolean validScope(File file) throws IOException {
        return validScope(file, null);
    }

    private boolean validScope(File file, IChannel channel) throws IOException {
        String absoluteFilePath = file.getCanonicalPath().toLowerCase();

        boolean foundParentPath = false;
        for (String path : audioPaths) {
            String absoluteValidPath = new File(path).getCanonicalPath().toLowerCase();

            if (absoluteFilePath.startsWith(absoluteValidPath)) {
                foundParentPath = true;
                break;
            }
        }

        if (!foundParentPath && channel != null) {
            sendMessage(channel, "That file location is out of the allowed scope!");
        }

        return foundParentPath;
    }

    private void quit() throws DiscordException {
        // sendMessageToPrimaryChannel("Bye bye!");
        client.logout();

        System.exit(0);
    }
}
