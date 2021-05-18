package mobileCameras;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.engine.watcher.Watch;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.grid.BouncyBorders;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.WrapAroundBorders;
import repast.simphony.util.ContextUtils;

/**
 * For Mac with M1 chip
 * Note that after any change, we need to first switch to java 11 and run, 
 * After it crashes, then switch back to java 8.
 * This should allow us to run successfully
 * 
 * schedule:
 * camera step()
 * camera clearMsg()
 * @author Nann
 * 
 * 
 */

public class JZombiesBuilder implements ContextBuilder<Object> {
	
	@Override
	public Context build(Context<Object> context) {
		context = new MyContext();
		
		context.setId("mobileCameras");
		
		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("communication network", context, false);
		Network<Object> net = netBuilder.buildNetwork();
		
		NetworkBuilder<Object> netBuilderCoverage = new NetworkBuilder<Object>("coverage network", context, true);
		Network<Object> covNet = netBuilderCoverage.buildNetwork();
		
		
		ContinuousSpaceFactory spaceFactory = 
				ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space = 
				spaceFactory.createContinuousSpace("space", context, 
						new RandomCartesianAdder<Object>(),
						new repast.simphony.space.continuous.BouncyBorders(),
						50, 50);
		
		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid("grid", context,
				new GridBuilderParameters<Object>(new BouncyBorders(),
						new SimpleGridAdder<Object>(),
						true, 50, 50));
		
		Parameters params = RunEnvironment.getInstance().getParameters();
		int zombieCount = params.getInteger("camera_count");
		int cameraRange = 10;
		for (int i = 0; i < zombieCount; i++) {
			context.add(new Camera(i, space, grid, cameraRange));
		}
		
		int humanCount = params.getInteger("human_count");
		double humanSpeed = 1;
		for (int i = 0; i < humanCount; i++) {
			int angle = RandomHelper.nextIntFromTo(0, 360);
			context.add(new Human(i, space, grid, angle, humanSpeed, 999));
		}
		
		// add edge with weight 0 to every pair of cameras
		Stream<Object> s = context.getObjectsAsStream(Camera.class);
		List<Object> camList = s.collect(Collectors.toList());
		while (camList.size() > 1) {
			Object tmp = camList.remove(0);
			for (Object cam : camList) {
				net.addEdge(tmp, cam, 0);
			}
		}
	
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj, (int)pt.getX(), (int)pt.getY());
		}
		
		
		boolean redirectOutput = true;
		if (redirectOutput) {
			// Redirecting printing to file
			File directory = new File("./trace");
			if (! directory.exists()) {
				directory.mkdir();
			}
			File file = new File("./trace/sample.txt");
	        PrintStream stream;
			try {
				stream = new PrintStream(file);
				System.out.println("All \"System.out\" is directed to "+file.getAbsolutePath());
		        System.setOut(stream);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	    
		return context;
	}
	

}