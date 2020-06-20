# httputil

http utilities - query twitch streams, reboot virgin media router

build using maven, and copy the dependencies directory to installation location

## twitch

queries twitch api to see who is live

the main advantage over the following list in twitch is:
* displays results in alphabetical order, rather than viewer count order
* ability to ignore streamers playing a game you don't like

create a properties file with keys like this

	clientid=x (where x is the client-id) 
	oauth=x (where x is the oauth token)
	cols=x (where x is max number of output columns, e.g. cols=80)
	s.x=y (where x is unique, and y is streamer name, e.g. s.01=lirik)
	gi.x=y (where x is unique, and y is game name to ignore, e.g. gi.01=fortnite)
	
Unfortunataly you now require a twitch account because you must specify an oauth token in the requests

you can generate both by logging in to twitch and going to https://twitchapps.com/tmi/

the http query headers give the client-id (enable chrome dev tools network tab and click on first column)

the response gives the oauth token (remove the oauth: prefix)

## vmreboot

log into a virgin media super hub router and reboot

you need to provide the host address and password on the command line

consider using a scheduled task/cron job

## streamlink

wrapper for running [streamlink](https://github.com/streamlink/streamlink) in a loop
