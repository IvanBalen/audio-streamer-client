This is a simple, asynchronous (console) streaming player based on the WebFlux API. The streaming client can also be used synchronously
just by changing the livePlay flag to false. This was done on purpose to demonstrate the differences between synchronous and
asynchronous playback.

By studying the code, you can learn quite a few important concepts regarding asynchronous communication such as

* buffer management especially if you have to prepare buffers of certain size and this is necessary for audio
* thread synchronization (again this is super important as your playback thread will typically run as a background thread)
* latency issues (if your streaming publisher can't keep up with how fast your consumer is using resources) and how to fix them
* sentinel values (you may need some "flags" that will signal your consumer that the current track playback has finished etc)

When you start the app, you'll get a list of genres and tracks available for playback. Now, to proceed you have to 
enter the tracks in the correct format e.g.

<span style="color: rgb(0, 0, 255);">jazz:Squarepusher_Just a Souvenir_01_Star Time 2.wav,Squarepusher_Just a Souvenir_02_The Coathanger.wav;rock:NeilYoung_OntheBeach.wav
</span>

Once the playback starts, you can control the audio by pausing it, resuming it or moving to the next track (if available)

