package com.ociweb.pronghorn.stage.network;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.HTTPClientRequestStage;
import com.ociweb.pronghorn.network.HTTPModuleFileReadStage;
import com.ociweb.pronghorn.network.ModuleConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderKeyDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ReleaseSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.scheduling.FixedThreadsScheduler;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;
import com.ociweb.pronghorn.stage.test.ConsoleSummaryStage;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class HTTPSRoundTripTest {


    private static final int groups = 1;//2;
    private static final int apps = 1; 
      
    
	@Ignore
	public void roundTripTest() {
				
//		String testFile = "OCILogo.png";
//		int    testFileSize = 9572;
//				
		boolean isTLS = true;
		
		String testFile = "SQRL.svg";
		int    testFileSize = 0;
		
		String root = buildStaticFileFolderPath(testFile);
		
    	GraphManager gm = new GraphManager();
    	GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 1000);
    	GraphManager.enableBatching(gm);
    	
        /////////////////
        /////////////////
    	int base2SimultaniousConnections = 3;
    	final int maxListeners = 1<<base2SimultaniousConnections;
		ServerCoordinator serverCoord = new ServerCoordinator(groups, 8443, 15, maxListeners);//32K simulanious connections on server. 
    	
    	//TODO: the stages must STAY if there is work to do an NOT return !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    	
    	
		 int requestUnwrapUnits = 1;
		 int responseWrapUnits = 4; //only have 4 users now...
		 int pipesPerOutputEngine = 1;
 
    	gm = defineServer(isTLS, root, gm, maxListeners, requestUnwrapUnits, responseWrapUnits, pipesPerOutputEngine, serverCoord);     
        
        /////////////////
      	
		final int inputsCount = maxListeners;//4;//also number of max connections		
		int maxPartialResponses = maxListeners;//4;
		
		final int outputsCount = 1;//2;//must be < connections
		
		//holds new requests
		Pipe<NetRequestSchema>[] input = new Pipe[inputsCount];
		GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 20_000);
		
		ClientCoordinator clientCoord = new ClientCoordinator(base2SimultaniousConnections, maxPartialResponses, isTLS);		
		
		int responseUnwrapUnits = 1;//To be driven by core count
		int requestWrapUnits = 1;//To be driven by core count
		
		
		Pipe<NetResponseSchema>[] toReactor = defineClient(isTLS, gm, base2SimultaniousConnections, outputsCount, maxPartialResponses, input,
				                                           clientCoord, responseUnwrapUnits, requestWrapUnits,
				                                           8, 8);
		     
		PipeCleanerStage<NetResponseSchema> cleaner = new PipeCleanerStage<>(gm, toReactor, "Reactor");
		final StageScheduler scheduler = setupScheduler(gm, serverCoord, clientCoord);

		
		long start = System.currentTimeMillis();
		
		scheduler.startup();
		
        runTestData(testFile, testFileSize, maxListeners, input, cleaner, scheduler, start);
        

	}

	private void runTestData(String testFile, int testFileSize, final int maxListeners, Pipe<NetRequestSchema>[] input,
			PipeCleanerStage<NetResponseSchema> cleaner, StageScheduler scheduler, long start) {
		//		try {
		//		Thread.sleep(1000_000);
		//	} catch (InterruptedException e1) {
		//		// TODO Auto-generated catch block
		//		e1.printStackTrace();
		//	}
				
				//test this on jdk 9
				//-Djdk.nio.maxCachedBufferSize=262144
						
		        
				final int MSG_SIZE = 6;
				
				//TODO: thread scheduler grouping
				//TODO: muti response pattern for PET integration
				
				
				int testSize = 1000;
								//250;
				              // 250_000;//300_000; //TODO: must be small enough to hold in queue.
				
				int expectedData = testSize*testFileSize;
				
				int requests = testSize;		
				
				long timeout = System.currentTimeMillis()+(testSize*20); //reasonable timeout
			
				int d = 0;
				
				//Histogram hist = new Histogram(2);
				//long[] startTimes = new long[testSize];
				
				//TODO: need stage which does both produce and consume to capture Histogram
			    
		
				while (requests>0 && System.currentTimeMillis()<timeout) {
								
					Pipe<NetRequestSchema> pipe = input[requests%input.length];
								
					if (PipeWriter.tryWriteFragment(pipe, NetRequestSchema.MSG_HTTPGET_100)) {
		
						PipeWriter.writeUTF8(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2, "127.0.0.1");
		
						int user = requests % maxListeners;
						PipeWriter.writeInt(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_LISTENER_10,  //0);
						                                                                               user);
						
						PipeWriter.writeUTF8(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, "/"+testFile);
						PipeWriter.writeInt(pipe, NetRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1, 8443);
						PipeWriter.publishWrites(pipe);
						
						requests--;				
					//	startTimes[requests] = System.nanoTime();
						
						d+=MSG_SIZE;
						
					} else {	
						Thread.yield();
					}
				}				
		
				//count total messages, we know the parser will only send 1 message for each completed event, it does not yet have streaming.
		
				System.out.println("--------------------------    watching for responses");
		
				requests = testSize;	
				
				
				
				int expected = MSG_SIZE*(testSize);
						
				int count = 0;
				int lastCount = 0;
				long nextNotice = System.currentTimeMillis()+2000;
				do {
					try {
						Thread.sleep(2);
					} catch (InterruptedException e) {
						break;
					}
					
					count = (int)cleaner.getTotalSlabCount();
					
					long now = System.currentTimeMillis();
					if (count!=lastCount) {
						
		//				int responseCount = (count-lastCount)/MSG_SIZE;
		//				long now2 = System.nanoTime();
		//				while (--responseCount>=0) {					
		//					hist.recordValue(now2-startTimes[--requests]);					
		//				}
						
						lastCount = count;
						
						if (now>nextNotice) {				
							System.err.println("pct "+((100f*lastCount)/(float)expected));
							nextNotice = now+2000;
						}
					} else {
						if (now>(nextNotice+40_000)) {
							System.err.println("value is no longer changing, break. msg total: "+(count/MSG_SIZE));
							break;
						}
					}
		
				} while (count<expected /*&& System.currentTimeMillis()<timeout*/);
						
				
		//		//do not shut down this way because the handshake will get dropped midstream. The server does not know if or when client will respond.
		//		int z = input.length;
		//		while (--z>=0) {
		//			PipeWriter.publishEOF(input[z]);		
		//		}
				
		
			//	hist.outputPercentileDistribution(System.out, 0d);
				
				
				System.out.println("total bytes returned:"+cleaner.getTotalBlobCount()+" expected "+expectedData); //434_070  23_930_000
				
				long duration = System.currentTimeMillis()-start;
		
					
				
				System.out.println("duration: "+duration);
				System.out.println("ms per call: "+(duration/(float)(count/(float)MSG_SIZE)));
				
				scheduler.shutdown();
				scheduler.awaitTermination(60, TimeUnit.SECONDS);
		
				assertEquals("Killed by timeout",expected,count);
				assertEquals(expected, lastCount);
	}


	
	
	@Ignore
	public void roundTripTest2() {
				
//		String testFile = "OCILogo.png";
		{
			//String testFile = "SQRL.svg";
			String testFile = "groovySum.json"; //shows that JSON works and finds the limiter
			
			
			boolean isTLS = false;//false;//
			
			String root = buildStaticFileFolderPath(testFile);
			
	    	GraphManager gm = new GraphManager();
	    	GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 900);
	    	
	    	//GraphManager.enableBatching(gm);
	    	
	        /////////////////
	        /////////////////
	    	int base2SimultaniousConnections = 6;//TODO: 14 is out of memory. 9 hang crash
	    	
	    	//TODO: we need a better test that has each users interaction of 10 then wait for someone else to get in while still connected.
	    	//TODO: urgent need to kill off expired pipe usages.
	    	//TODO: urgent must split testing client and server!!
	    	//each client pipe is 1 user no more.
	    	
	    	final int totalUsersCount = 1<<base2SimultaniousConnections;
	    	final int loadMultiplier = 10_000;//100_000;//100_000;
	    	
	    	//TODO: this number must be the limit of max simuantious handshakes.
	    	int maxPartialResponsesServer = 64; //input lines to server (should be large)
	    	int maxPartialResponsesClient = 64; //input lines to client (should be large)
	    	
	    	//client output count of pipes, this is the max count of handshakes from this client since they block all following content.
	    	final int clientOutputCount = 1;//8;//8;//should be < client connections,  number of pipes getting wrappers and sent out put stream 

	    	final int maxConnectionBitsOnServer = 15;//32K simulanious connections on server
	    	
	    	int requestUnwrapUnits = 2;  //server unwrap units - need more for handshaks and more for posts
	    	int responseWrapUnits = isTLS?4:32;   //server wrap units - need more for get requests, NOTE also number of pipes when TLS is OFF going to ServerWriter
	    	int pipesPerOutputEngine = 1;//multiplier against server wrap units for max simultanus user responses.
	    		    	
	    	
	    	//This must be large enough for both partials and new handshakes.
			ServerCoordinator serverCoord = new ServerCoordinator(groups, 8443, maxConnectionBitsOnServer, maxPartialResponsesServer);
		
	    	
	    	gm = defineServer(isTLS, root, gm, maxPartialResponsesServer, requestUnwrapUnits, responseWrapUnits, pipesPerOutputEngine, serverCoord);     
	        
	        /////////////////
	      	
	    	
	    	int clientResponseUnwrapUnits = 4;//To be driven by core count,  this is for consuming get responses
	    	int clientRequestWrapUnits = 4;//To be driven by core count, this is for production of post requests

	    	int responseQueue = 64; //longer to support all the requesters, if this is short it may hang on write...
	    	int requestQueue = 16;
	    	
	    	//TODO: buffer is overflow to stop from dropping messages must make buffers bigge?
	    	int inFlightLimit = 3000;//when set to much more it disconnects.
						
			
			//holds new requests
			Pipe<NetRequestSchema>[] input = new Pipe[totalUsersCount];
			GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 20_000);
			
			int usersBits = 0;//this feature does not work.
			int usersPerPipe = 1<<usersBits;  
			ClientCoordinator clientCoord = new ClientCoordinator(base2SimultaniousConnections<<usersBits, maxPartialResponsesClient, isTLS);		//TODO: add one here!!!
								
			
			
			Pipe<NetResponseSchema>[] toReactor = defineClient(isTLS, gm, base2SimultaniousConnections, clientOutputCount, maxPartialResponsesClient, 
					                                           input, clientCoord, clientResponseUnwrapUnits, clientRequestWrapUnits,
					                                           requestQueue, responseQueue);
			assert(toReactor.length == input.length);
			
			//TODO: test without encryption to find pure latency of framework.
			
			//NOTE: must test with the same or more test size than the users we want to test above.
			
			//2K is optimal? balance between handshake and optimizatios, 256 calls per client ..68 ms per result so 174ms for all + 268 latency , 442ms
			int testSize = totalUsersCount*loadMultiplier; 
		
			RegulatedLoadTestStage client = new RegulatedLoadTestStage(gm, toReactor, input, testSize, inFlightLimit, "/"+testFile, usersPerPipe);
			
			if (base2SimultaniousConnections<=6) {
				GraphManager.exportGraphDotFile(gm, "HTTPSRoundTripTest");			
	        	MonitorConsoleStage.attach(gm); 
			}
	        
	        
			final StageScheduler scheduler = setupScheduler(gm, serverCoord, clientCoord);
			
			long start = System.currentTimeMillis();
			scheduler.startup();
		
	        
	        /////////////////
	        /////////////////
	
			GraphManager.blockUntilStageBeginsShutdown(gm,  client);		
	
		//	hist.outputPercentileDistribution(System.out, 0d);
			
			
		//	System.out.println("total bytes returned:"+cleaner.getTotalBlobCount()+" expected "+expectedData); //434_070  23_930_000
			
			long duration = System.currentTimeMillis()-start;
				
			
			System.out.println("duration: "+duration);
			float msPerCall = duration/(float)testSize;
			System.out.println("ms per call: "+msPerCall);		
			System.out.println("calls per sec: "+(1000f/msPerCall));
			
			scheduler.shutdown();
			scheduler.awaitTermination(3, TimeUnit.SECONDS);
		}
		System.gc();
	}
	
	
	private String buildStaticFileFolderPath(String testFile) {
		URL dir = ClassLoader.getSystemResource(testFile);
		String root = "";	//file:/home/nate/Pronghorn/target/test-classes/OCILogo.png
						
		try {
		
			String uri = dir.toURI().toString();			
			root = uri.substring("file:".length(), uri.lastIndexOf('/'));
			
		} catch (URISyntaxException e) {						
			e.printStackTrace();
			fail();
		}
		return root;
	}


	private GraphManager defineServer(boolean isTLS, String root, GraphManager gm, int maxSimultaniousClients, int requestUnwrapUnits, int responseWrapUnits,  int pipesPerOutputEngine,
			ServerCoordinator coordinator) {
		final String path = root;
    	
    	ModuleConfig config = new ModuleConfig() {

 			@Override
 			public long addModule(int a, GraphManager graphManager, Pipe<HTTPRequestSchema> input,
 					Pipe<ServerResponseSchema> output,
 					HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderKeyDefaults> spec) {
 				
 				HTTPModuleFileReadStage.newInstance(graphManager, input, output, spec, path);
 				
 				//return needed headers
 				return 0;
 			}

 			@Override
 			public CharSequence getPathRoute(int a) {
 				return "/%b";
 			}        
         	
         };

         int socketWriters = 1;
         
        gm = NetGraphBuilder.buildHTTPServerGraph(isTLS, gm, groups, maxSimultaniousClients, apps, config, coordinator, requestUnwrapUnits, responseWrapUnits, pipesPerOutputEngine, socketWriters);
		return gm;
	}


	private StageScheduler setupScheduler(GraphManager gm, final ServerCoordinator serverCoord, final ClientCoordinator clientCoord) {

       
        final StageScheduler scheduler = new ThreadPerStageScheduler(gm);
        
      //  final StageScheduler scheduler = new FixedThreadsScheduler(gm, 16); TOOD: fix this to limit threads in use
        
        
                       
        //TODO: add this to scheduler so its done everywehre by default!!  TODO: urgent.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                    scheduler.shutdown();
                    scheduler.awaitTermination(1, TimeUnit.SECONDS);

                    serverCoord.shutdown();
                    clientCoord.shutdown();
            }
        });
		return scheduler;
	}
	


	private Pipe<NetResponseSchema>[] defineClient(boolean isTLS, GraphManager gm, int bitsPlusHashRoom,
			int outputsCount, int maxPartialResponses, Pipe<NetRequestSchema>[] input, ClientCoordinator ccm, int responseUnwrapUnits, int requestWrapUnits,
			int requestQueue, int responseQueue) {
				
		int requestQueueBytes = 1<<4;
		int responseQueueBytes = 1<<18;
		
		//one of these per unwrap unit and per partial message, there will be many of these normally so they should not be too large
		//however should be deeper if we are sending lots of messages
		int netRespQueue = 16;
		int netRespSize = 1<<17;//must be just larger than the socket buffer

		int httpRequestQueueBytes = 1<<13;
		int httpRequetQueueSize = 256;
		
		int usersPerPipe = 10; //need to set hash lookup.

		
		//create more pipes if more wrapers were requested.
		if (requestWrapUnits>outputsCount) {
			outputsCount = requestWrapUnits;
		}
		//out to the server, one of these for every client user
		PipeConfig<NetRequestSchema> netRequestConfig = new PipeConfig<NetRequestSchema>(NetRequestSchema.instance, requestQueue, requestQueueBytes);
		//System.err.println("in "+netRequestConfig);
		//back from server, one of these for every client user.
		PipeConfig<NetResponseSchema> netResponseConfig = new PipeConfig<NetResponseSchema>(NetResponseSchema.instance, responseQueue, responseQueueBytes);
		//System.err.println("out "+netResponseConfig);	
		
		
		IntHashTable listenerPipeLookup = new IntHashTable(bitsPlusHashRoom+2); //bigger for more speed.
		
		
		int i = input.length;//*usersPerPipe;
		while (--i>=0) {
			IntHashTable.setItem(listenerPipeLookup, i, i/*%input.length*/);//put this key on that pipe			
		}				
		
		//second pipe which also impacts latency		
		PipeConfig<NetPayloadSchema> httpRequestConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,httpRequetQueueSize,httpRequestQueueBytes); 
		
		//////////////
		//these 2 are small since we have so many
		/////////////
		
		
		//responses from the server	
		Pipe<NetResponseSchema>[] toReactor = new Pipe[input.length];	
				
		int m = input.length;
		while (--m>=0) {
			toReactor[m] = new Pipe<NetResponseSchema>(netResponseConfig);
			input[m] = new Pipe<NetRequestSchema>(netRequestConfig);	
		}

		
		Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
		int r = outputsCount;
		while (--r>=0) {
			clientRequests[r] = new Pipe<NetPayloadSchema>(httpRequestConfig);		
		}
		

		NetGraphBuilder.buildHTTPClientGraph(isTLS, gm, 
				                             maxPartialResponses, ccm, listenerPipeLookup,
				                             netRespQueue,netRespSize,
											 clientRequests, 
											 toReactor, 
											 responseUnwrapUnits, requestWrapUnits);

		new HTTPClientRequestStage(gm, ccm, input, clientRequests);
		
		//TODO: JUST LIKE GROUPS THESE CAN NOT ACCESS ccm AT THE SAME TIME.
//		Pipe[][] inputs = Pipe.splitPipes(2, input);
//		Pipe[][] requests = Pipe.splitPipes(2, clientRequests);
//				
//		
//		new HTTPClientRequestStage(gm, ccm, inputs[0], requests[0]);
//		new HTTPClientRequestStage(gm, ccm, inputs[1], requests[1]);
//				
		
		return toReactor;
	}

	
}
