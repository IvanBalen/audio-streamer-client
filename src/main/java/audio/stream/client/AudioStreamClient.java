package audio.stream.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;


public class AudioStreamClient {
	
	private final String baseAudioUrl = "http://localhost:8080/audio/stream/";
    
    private volatile boolean paused = false;
    
    private volatile boolean stopped = false;
    
    private List<TrackPath> tracksToPlay;
    
    private Thread playbackThread;
    
    private AudioInputStream audioStream;
    
    private SourceDataLine audioLine;
    
    private  final  boolean livePlay = true;
    
    private BlockingQueue<byte[]> audioChunkQueue = new LinkedBlockingQueue<>();
    
    private int frameSize;
    
    private class TrackPath {
    	
    	String genre;
    	
    	String trackName;
    	
    	TrackPath(String genre, String trackName){
    		this.genre = genre;
    		this.trackName = trackName;
    	}
    }

	public static void main(String[] args) throws IOException, UnsupportedAudioFileException, LineUnavailableException, InterruptedException {
		// TODO Auto-generated method stub
		AudioStreamClient player = new AudioStreamClient();
		
		Map<String,List<String>> allTracks = player.getAllTracks();
		
		allTracks.keySet().forEach( key -> {
			System.out.println(key + ".");
			allTracks.get(key).forEach( System.out::println );
		});
		
		System.out.println("To play a track, choose a genre followed by track names, e.g. jazz:track1,track2;rock:track3");
		
		Scanner scanner = new Scanner(System.in);
	
        try {	
        
        	String command = scanner.nextLine().trim();
    		player.tracksToPlay = player.parseCommand(command);
    		
    		try {
    			if(player.livePlay) {
    				if( !player.streamAndPlayAudioAsync() )
    					return;
    			}else {
    				if( !player.streamAndPlayAudio() )
    					return;
    			}
			} catch (Exception e) {
				System.out.println("err: " + e.getMessage());
			}
        	
            while (true) {
            	
        		
                System.out.println("Commands: (p)ause, (r)esume, (n)ext, e(x)it");
                command = scanner.nextLine().trim().toLowerCase();
       
                switch (command) {
                   
                    case "p": 
                    	player.pause(); 	
                    	break;
                    case "r": 
                    	player.resume(); 	
                    	break;
                    case "n": 
                    	player.stop(); 
                    	break;
                    case "x":
                        player.stop();
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Unknown command");
                }//end switch
        	}//end while
       

        } catch (Exception e) {
            e.printStackTrace();
        }finally{
        	 if(scanner != null)
             	scanner.close();
        }
		
    }
	/**
	 * parses user input in the form genre1:track1,track2;genre2:track1,track2 etc and populates the List<TrackPath> which holds the track to be download
	 * from the server
	 * @param command
	 * @return - list of tracks (urls to audio tracks)
	 */
        private List<TrackPath> parseCommand(String command) {
		
		List<TrackPath> urlParams = new LinkedList<>();
		String[] genreParsed = command.split(";");
		
		for(String s : genreParsed) {
			
			String[] trackParsed = s.split("[:,]");
			
			for(int i=1;i<trackParsed.length;++i)
				urlParams.add(new TrackPath(trackParsed[0] , trackParsed[i] ) );
			
		}
		return urlParams;
		
	}
        /**
         * this method loads the audio line (device) from the stream downloaded from the server (through getStream)
         * @param audioUrl
         * @throws IOException
         * @throws LineUnavailableException
         * @throws UnsupportedAudioFileException
         * @throws InterruptedException
         */
	private void loadAudio(String audioUrl) throws IOException, LineUnavailableException, UnsupportedAudioFileException, InterruptedException {
		
		 //this is where we use the audio written to outputStream and send it to the audio device(line) for playback
 
		ByteArrayOutputStream outputStream = getStream(audioUrl);
	
		try (InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
            audioStream = AudioSystem.getAudioInputStream(inputStream);
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
    
        }
	}
	private int getFrameSize() {
		return audioStream.getFormat().getFrameSize();
	}
	private void loadAudioAsync(byte[] stream) throws IOException, LineUnavailableException, UnsupportedAudioFileException, InterruptedException {
					
		try (InputStream inputStream = new ByteArrayInputStream(stream)) {
           audioStream = AudioSystem.getAudioInputStream(inputStream);
           AudioFormat format = audioStream.getFormat();
           DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
           audioLine = (SourceDataLine) AudioSystem.getLine(info);
           audioLine.open(format);
   
       }
	}
	/**
	 * this method does 3 things 1) parses user input (tracks to be played), 2) streams the tracks from the server and 3) plays the current track
	 * @return true if there are tracks to played , otherwise false
	 * @throws IOException
	 * @throws LineUnavailableException
	 * @throws UnsupportedAudioFileException
	 * @throws InterruptedException
	 * 
	 */
	private  boolean streamAndPlayAudio() throws IOException, LineUnavailableException, UnsupportedAudioFileException, InterruptedException {
		
		if(tracksToPlay.isEmpty()) {
			System.out.println("No tracks selected - exiting app");
			return false;
		}
		//parse tracks from user input
		final TrackPath trackPath = tracksToPlay.remove(0);
		String audioUrl = baseAudioUrl + trackPath.genre + "/" + trackPath.trackName;
		//stream audio
		loadAudio(audioUrl);	
		//play audio in a background thread
        System.out.println(trackPath.trackName + " ready - playback should start shortly: " );
        play();
        return true;
	}

public  boolean streamAndPlayAudioAsync() throws UnsupportedAudioFileException, IOException, InterruptedException, LineUnavailableException {
	
    
	if(tracksToPlay.isEmpty()) {
		System.out.println("No tracks to play - exiting app");
		//Thread.sleep(1000);
		return false;
	}
	//parse tracks from user input
	final TrackPath trackPath = tracksToPlay.remove(0);
	String audioUrl = baseAudioUrl + trackPath.genre + "/" + trackPath.trackName;
	System.out.println("processing track " + trackPath.trackName);

	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	CountDownLatch headerLoaded = new CountDownLatch(1);
	
	WebClient.builder().build().get()
    .uri(audioUrl)
    .retrieve()
    .bodyToFlux(DataBuffer.class)
    .doOnComplete( () -> {
        	try {
        		audioChunkQueue.put(new byte[0]); // sentinel value used to stop our playback
        	} catch (InterruptedException e) {
        		Thread.currentThread().interrupt();
        	}
    	})
    .index()
    .subscribe(tuple -> {
        try {
        	//rock:NeilYoung_OntheBeach.wav;jazz:Squarepusher_Just a Souvenir_02_The Coathanger.wav
        	//jazz:Squarepusher_Just a Souvenir_02_The Coathanger.wav;rock:NeilYoung_OntheBeach.wav
        	 final long index = tuple.getT1();
             final DataBuffer buffer = tuple.getT2();
             
             //validLength must be chosen/calculated so that the number of frames passed to the audio line is an integer
             //for this reason, we can't just pass any number of bytes to AudioLine.write for playback
             int validLength;
             int totalLen = outputStream.size();
             int currLen;
             int headerLen;
             
             byte[] chunk = new byte[buffer.readableByteCount()];
             buffer.read(chunk);
			
             if ( index == 0 ) {
			 //the first chunk contains the wave header, which has the info about the format of the audio (e.g 16bit, mono/stereo etc), 
				//the header length tells how long (in bytes) the header is -- this is important because now we know where
            	 //the actual samples start (at what offset into the audio)
            	headerLen = getHeaderLen(chunk);				
				loadAudioAsync(chunk);
				frameSize = getFrameSize();
				validLength = ((chunk.length - headerLen) / frameSize) * frameSize;
				
				outputStream.write(chunk, headerLen, validLength);
				audioChunkQueue.put(outputStream.toByteArray());
				outputStream.reset();
				//keep the leftover bytes
				outputStream.write(chunk, headerLen+validLength, chunk.length-(validLength+headerLen));
				headerLoaded.countDown();
			}else {
				
				//totalLen is the length of the current chunk plus the length of whatever bytes are still remaining from
				//the previous chunk
				totalLen += chunk.length;
				validLength = (totalLen / frameSize) * frameSize;
				currLen = (validLength == totalLen) ? chunk.length : chunk.length-(totalLen-validLength);
				outputStream.write(chunk, 0, currLen);
				audioChunkQueue.put(outputStream.toByteArray());
				outputStream.reset();
				outputStream.write(chunk, currLen, chunk.length-currLen);
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
            DataBufferUtils.release(tuple.getT2());
			
        }
    });
	//we have to wait until the header is fully processed before playing the audio
	headerLoaded.await();
	play();
	return true;
}

	private  boolean isData(byte[] section, int offset) {
		
		final byte[] data = { 'd','a','t','a' };
		for(int i=0;i<data.length;++i) {
			if(section[i+offset] != data[i])
				return false;
		}
		
		return true;
		
	}
	/**
	 * this method parses the audio header for the "data" chunk and returns the offset to the actual audio samples
	 * typically, the samples will start at the "data" offset plus 8  bytes
	 * @param chunk
	 * @return
	 */
	private int getHeaderLen(byte[] chunk) {
		
		final int dataSectionLen = 8;
		
		for(int i=0; i<chunk.length; i++){
			if(isData(chunk, i)){
				return i  + dataSectionLen;
			}
		}
		
		return 0;
	}
/**
 * this method will asynchronously download the entire audio, however playback won't start until the download has been completed
 * @param audioUrl
 * @return
 * @throws UnsupportedAudioFileException
 * @throws IOException
 * @throws InterruptedException
 * @throws LineUnavailableException
 */
public ByteArrayOutputStream getStream(String audioUrl) throws UnsupportedAudioFileException, IOException, InterruptedException, LineUnavailableException {
	
	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CountDownLatch downloadCountdown = new CountDownLatch(1);
    //this part will download the audio and write it to the output stream  
    WebClient.builder().build().get()
        .uri(audioUrl)
        .retrieve()
        .bodyToFlux(DataBuffer.class)
        .doOnNext(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                DataBufferUtils.release(buffer); //if we don't do this, we'll have a leak
            }

        })
        .doOnComplete(downloadCountdown::countDown)
        .subscribe();
    //we wait for the download to fully complete before returning the output stream (so no live stream/play)
    downloadCountdown.await();
    return outputStream;

}
	
	/**
	 * this method spawns a new thread, which does the actual playback. It also advances playback to the next track
	 */
		public void play() {
	        if (playbackThread != null && playbackThread.isAlive() && !stopped) {
	            resume();
	            return;
	        }

	        paused = false;
	        stopped = false;
	 
	        playbackThread = new Thread(() -> {

	            try {
	            	
	              if(!livePlay) {
	            	  
	  	            int bytesRead;  
	            	audioLine.start();
	            	byte[] buffer = new byte[4096];
	                while (!stopped && (bytesRead = audioStream.read(buffer, 0, buffer.length)) != -1) {
	                    synchronized (this) {
	                        while (paused) {
	                            wait();
	                        }
	                    }
	                    audioLine.write(buffer, 0, bytesRead);
	                }
	              }else {
	            	  
	            	  audioLine.start();
	            	  while (!stopped /*&& !audioChunkQueue.isEmpty()*/ ) {
	            		  //note that audioChunkQueue.take will wait if there's no more elements
	            		  byte[] audioChunk = audioChunkQueue.take();
	            		  if(audioChunk.length == 0) //this means we've reached the end of our track
	            			  break; 
	            		  synchronized (this) {
		                        while (paused) {
		                            wait();
		                        }
		                    }
		                    audioLine.write(audioChunk, 0, audioChunk.length);
	            		  
	            	  }	            	  
	              }
	            } catch (IOException | InterruptedException e) {
	                e.printStackTrace();
	            } finally {
	            	//when the playback thread is about to be destroyed, we free the resources and automatically
	            	//advance to the next track (if available) by calling streamAndPlayAudio() or streamAndPlayAudioAsync
	            	
	            	stopped 	= true;
	              //  audioLine.drain();
	                audioLine.stop();
	                audioLine.close();
	                
	                try {	                   
	                    	audioStream.close();

	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	                try {
	                	if(!livePlay) {
	                		if(!streamAndPlayAudio())
	                			return;
	                	}else {
	                		
	                		audioChunkQueue.clear();
	                		if(!streamAndPlayAudioAsync())
	                			return;
	                	}
					
					} catch (Exception e) {
						e.printStackTrace();
					}
	         
	               System.out.println("finishing track ...");
	            }
	        });

	        playbackThread.start();
	        System.out.println("Playing...");
	    }
		/**
		 * we're pausing the playback simply by setting the pause flag to true and using this flag in combination with 
		 * wait() called on the playback thread
		 */
	    public synchronized void pause() {
	        paused = true;
	        System.out.println("Paused.");
	    }
	    /**
	     * we resume playback by waking up the playback thread
	     */
	    public synchronized void resume() {
	        if (paused) {
	            paused = false;
	            notify();
	            System.out.println("Resumed.");
	        }
	    }
	    
	    public void stop() {
	        stopped = true;
	        paused = false;
	        if (playbackThread != null) {
	            playbackThread.interrupt();
	            try {
	                playbackThread.join(); // Wait for thread to finish
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	        System.out.println("Current track stopped - next track will start if available.");
	    }
	    /*
	     * this method will return all the tracks in a Map object where the keys represent genres and values tracks
	     */
	public Map<String, List<String>> getAllTracks(){
		
		WebClient client = WebClient.builder().build();

		Map<String, List<String>> tracks = (Map<String, List<String>>) client.get()
        .uri("http://localhost:8080/audio/allTracks")
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<Map<String, List<String>>>() {}).block();		
		return tracks;
		
	}
	/**
	 * this method retrieves all the tracks for a specified genre
	 * @param genre 
	 * @return - list of tracks for the specified genre
	 */
	public List<String> getTracksPerGenre(String genre){
		
		WebClient client = WebClient.builder().build();
        
		List<String> tracks = (List<String>) client.get()
        .uri("http://localhost:8080/audio/allGenreTracks/" + genre)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<String>>() {}).block();		
		return tracks;
		
	}
	/**
	 * 
	 * @return list of available genres of music
	 */
	public static List<String> getGenres(){
		
		WebClient client = WebClient.builder().build();
        
		List<String> genres = (List<String>) client.get()
        .uri("http://localhost:8080/audio/genres")
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<String>>() {}).block();
		
		
		return genres;
	}
	
	

}
