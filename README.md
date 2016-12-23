# Mayushii Bot

### Project set up

1. Import the project as a maven project.
2. Create a folder called secret.txt in the project root directory. Inside this folder paste your bots secret key.
3. If you already have a (or a few) folders for a previous bot that contains all your audio files you can link it to this bot by running it with the path to the folder as a command line argument. e.g. java -jar mayushii-bot.jar "C:/Users/Admin/Dropbox/Discord Soundboard 1" "C:/Users/Admin/Dropbox/Discord Soundboard 2"

### Bot set up

1. Go to the discord development website, navigate to my apps, and create a new app. https://discordapp.com/developers/applications/me
2. Set the app to be a bot.
3. Copy the Client ID, navigate to the bot set up page and add the bot to your server. https://discordapp.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&scope=bot