# httputil

http utilities - query twitch streams, reboot virgin media router

build using maven, and copy the dependencies directory to installation location

## twitch

queries [twitch api](https://github.com/justintv/Twitch-API/wiki/API) to see who is live

the main advantage over the following list in twitch is:
* displays results in alphabetical order, rather than viewer count order
* ability to ignore streamers playing a game you don't like
* don't need a twitch account (if you can scrape a client-id from somewhere)

create a properties file with keys like this

	clientid=x (where x is the value to use in the client-id header) 
	cols=x (where x is max number of output columns, e.g. cols=80)
	s.x=y (where x is unique, and y is streamer name, e.g. s.01=lirik)
	gi.x=y (where x is unique, and y is game name to ignore, e.g. gi.01=fortnite)

## vmreboot

log into a virgin media super hub router and reboot

you need to provide the host address and password on the command line

consider using a scheduled task/cron job
