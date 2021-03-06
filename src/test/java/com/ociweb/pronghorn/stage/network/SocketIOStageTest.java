package com.ociweb.pronghorn.stage.network;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.ClientSocketWriterStage;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.ServerNewConnectionStage;
import com.ociweb.pronghorn.network.schema.ReleaseSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.ServerConnectionSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class SocketIOStageTest {

	private static final int TIMEOUT = 30_000;//1 min
	
    private final int socketGroups = 1;  //only have 1 group listener for this test
    private final int socketGroupId = 0; //we only have the 0th reader in use.
    private final int maxtPartials = 5;//0; 
    private final int maxConnBits = 15;
	private final int testUsers = 17;
	////
	////test data, these are seeds and sizes to be sent in order by each user
	////
	
	private final int[] testSeeds = new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
	private final int[] testSizes = new int[]{1,2,4,8,16,32,64,128,256,512,1024,2048,4069,8192,16384,32768};
	
	
	@Ignore //debug why this does not complete
	public void roundTripATest() {		
		roundTripTest(true, 14089);		
	}

	@Ignore //debug why this does not complete
	public void roundTripBTest() {		
		roundTripTest(false, 15089);		
	}

	private void roundTripTest(boolean encryptedContent, int port) {

        
        PipeConfig<ServerConnectionSchema> newConnectionsConfig = new PipeConfig<ServerConnectionSchema>(ServerConnectionSchema.instance, 30);  
        PipeConfig<NetPayloadSchema> payloadPipeConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance, 20, 32768);
        PipeConfig<ReleaseSchema> releaseConfig = new PipeConfig<ReleaseSchema>(ReleaseSchema.instance,10);
		
        GraphManager gm = new GraphManager();
        
        ServerCoordinator serverCoordinator = new ServerCoordinator(socketGroups, port, maxConnBits, maxtPartials);
		ClientCoordinator clientCoordinator = new ClientCoordinator(                    maxConnBits, maxtPartials, false);
		
		
		///
		///server new connections e-poll
		///
        Pipe<ServerConnectionSchema> newConnectionsPipe = new Pipe<ServerConnectionSchema>(newConnectionsConfig);
        ServerNewConnectionStage.newIntance(gm, serverCoordinator, newConnectionsPipe, false); //no actual encryption so false.
        PipeCleanerStage.newInstance(gm, newConnectionsPipe);
        
        ////
        ////client to write data to socket
        ////   
        {
	        Pipe<NetPayloadSchema>[] input = new Pipe[]{new Pipe<NetPayloadSchema>(payloadPipeConfig)};		
	        ClientSocketWriterStage.newInstance(gm, clientCoordinator , input);
	        new SocketTestGenStage(gm, input, testUsers, testSeeds, testSizes, clientCoordinator, port);
        }

        
        ////
        ////server to consume data from socket and bounce it back to sender
        ////
        {
		    Pipe<NetPayloadSchema>[] output = new Pipe[maxtPartials];
		    int p = maxtPartials;
		    while (--p>=0) {
		    	output[p]=new Pipe<NetPayloadSchema>(payloadPipeConfig);
		    }	    
		    Pipe[] releasePipes = new Pipe[]{new Pipe<ReleaseSchema>(releaseConfig )};        
			ServerSocketReaderStage.newInstance(gm, releasePipes, output, serverCoordinator, socketGroupId, encryptedContent);	
	        new ServerSocketWriterStage(gm, serverCoordinator, output, releasePipes[0], 0); 
        }
		

		////
		//full round trip client takes data off socket
		////	
		PronghornStage watch = null;
		{
			Pipe[] releasePipes = new Pipe[]{new Pipe<ReleaseSchema>(releaseConfig )};   
			Pipe<NetPayloadSchema>[] response = new Pipe[maxtPartials];
		    int z = maxtPartials;
		    while (--z>=0) {
		    	response[z]=new Pipe<NetPayloadSchema>(payloadPipeConfig);
		    }
		    new ClientSocketReaderStage(gm, clientCoordinator, releasePipes, response, encryptedContent);
			watch = new SocketTestDataStage(gm, response, releasePipes[0], encryptedContent, testUsers, testSeeds, testSizes); 
		}
		
	    MonitorConsoleStage.attach(gm);
		
		/////////////////////////////////
		//run the full test on the JUnit thread until the consumer is complete
		//////////////////////////////
		run(gm, watch);
	}
		
	
	@Test
	public void clientToServerSocketATest() {
		clientToServerSocketTest(true,13081);
	}

	@Test
	public void clientToServerSocketBTest() {
		clientToServerSocketTest(false,12082);
	}
	
	private void clientToServerSocketTest(boolean encryptedContent, int port) {
		GraphManager gm = new GraphManager();

        
        //TODO: unit test must run with both true and false.
        
        PipeConfig<ServerConnectionSchema> newConnectionsConfig = new PipeConfig<ServerConnectionSchema>(ServerConnectionSchema.instance, 30);  
        PipeConfig<NetPayloadSchema> payloadPipeConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance, 20, 32768);
        PipeConfig<ReleaseSchema> releaseConfig = new PipeConfig<ReleaseSchema>(ReleaseSchema.instance,10);
        
        
		ServerCoordinator serverCoordinator = new ServerCoordinator(socketGroups, port, maxConnBits, maxtPartials);
		ClientCoordinator clientCoordinator = new ClientCoordinator(                    maxConnBits, maxtPartials,false);
					
		///
		///server new connections e-poll
		///
        Pipe<ServerConnectionSchema> newConnectionsPipe = new Pipe<ServerConnectionSchema>(newConnectionsConfig);
        ServerNewConnectionStage.newIntance(gm, serverCoordinator, newConnectionsPipe,false); //no actual encryption so false.
        PipeCleanerStage.newInstance(gm, newConnectionsPipe);
        
        ////
        ////server to consume data from socket
        ////
	    Pipe<NetPayloadSchema>[] output = new Pipe[maxtPartials];
	    int p = maxtPartials;
	    while (--p>=0) {
	    	output[p]=new Pipe<NetPayloadSchema>(payloadPipeConfig);
	    }
	    
	    Pipe[] acks = new Pipe[]{new Pipe<ReleaseSchema>(releaseConfig )};        
		ServerSocketReaderStage.newInstance(gm, acks, output, serverCoordinator, socketGroupId, encryptedContent);
        SocketTestDataStage watch = new SocketTestDataStage(gm, output, acks[0], encryptedContent, testUsers, testSeeds, testSizes); 
        
        ////
        ////client to write data to socket
        ////                
        Pipe<NetPayloadSchema>[] input = new Pipe[]{new Pipe<NetPayloadSchema>(payloadPipeConfig)};		
		ClientSocketWriterStage.newInstance(gm, clientCoordinator , input);
		new SocketTestGenStage(gm, input, testUsers, testSeeds, testSizes, clientCoordinator, port);
		

		GraphManager.exportGraphDotFile(gm, "UnitTest");
		MonitorConsoleStage.attach(gm);
		   
		/////////////////////////////////
		//run the full test on the JUnit thread until the consumer is complete
		//////////////////////////////
		run(gm, watch);
	}

	private void run(GraphManager gm, PronghornStage watch) {
		NonThreadScheduler scheduler = new NonThreadScheduler(gm);
		scheduler.startup();
		long limit = System.currentTimeMillis() + TIMEOUT;
		while (!GraphManager.isStageShuttingDown(gm, watch.stageId)) {
			scheduler.run();
			scheduler.checkForException();//will throw for unexpected exceptions discovered in the graph.
			if (System.currentTimeMillis()>limit) {
				scheduler.shutdown();
				Assert.fail("Timeout");
			}			
		}
		scheduler.shutdown();
	}
	
	

    
    
    
}
