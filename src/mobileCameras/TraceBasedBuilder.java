package mobileCameras;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactory;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.Schedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.continuous.RandomCartesianAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.grid.BouncyBorders;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;

/**
 * To use this builder, first run the repast,
 * Then in the GUI set Data Loaders,
 * In the "Select Data Source Type" window, click "Custom ContextBuilder Implementation"
 * The select this java class
 * 
 * @author Nann
 *
 */

public class TraceBasedBuilder implements ContextBuilder<Object> {

	@Override
	public Context build(Context<Object> context) {
		System.setOut(System.out);
		System.out.println("TraceBasedBuilder");
		
		context = new MyContext();

		context.setId("mobileCameras");

		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("communication network", context, false);
		Network<Object> net = netBuilder.buildNetwork();

		NetworkBuilder<Object> netBuilderCoverage = new NetworkBuilder<Object>("coverage network", context, true);
		Network<Object> covNet = netBuilderCoverage.buildNetwork();

		ContinuousSpaceFactory spaceFactory = ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null);
		ContinuousSpace<Object> space = spaceFactory.createContinuousSpace("space", context,
				new RandomCartesianAdder<Object>(), new repast.simphony.space.continuous.BouncyBorders(), 50, 50);

		GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
		Grid<Object> grid = gridFactory.createGrid("grid", context,
				new GridBuilderParameters<Object>(new BouncyBorders(), new SimpleGridAdder<Object>(), true, 50, 50));
		
		Parameters params = RunEnvironment.getInstance().getParameters();
			
		String initScenario = params.getString("init_scenario_path");
		Document scenario = MyUtil.parseScenarioXML(initScenario);

		int zombieCount = params.getInteger("camera_count");
		int cameraRange = 10;
		for (int i = 0; i < zombieCount; i++) {
			context.add(new Camera(i, space, grid, cameraRange));
		}


		// Get all covered objects from file
		HashMap<Integer, Element> covObjMap = MyUtil.returnCoveredObjects(scenario);
		
		// Use user provided seed when re-initializing humans
		int seedOriginal = RandomHelper.getSeed();
		
		int scenarioTime = Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));
		int humanSeed = params.getInteger("user_seed");
		int userInitSeed = (int) Math.pow(humanSeed, scenarioTime);
		RandomHelper.setSeed(userInitSeed);

		
		
		// Initialize object moving direction and position
		int humanCount = params.getInteger("human_count");
		double humanSpeed = 1;
		int angle = 0;
		double[] location = new double[2];

		for (int i = 0; i < humanCount; i++) {
			if (covObjMap.containsKey(i)) {
				// initialization of a covered object
				angle = Integer.parseInt(covObjMap.get(i).getAttribute("angle"));
				location[0] = Double.parseDouble(covObjMap.get(i).getAttribute("x"));
				location[1] = Double.parseDouble(covObjMap.get(i).getAttribute("y"));

				Human human = new Human(i, space, grid, angle, humanSpeed, humanSeed);
				context.add(human);
				space.moveTo(human, location);

				// if the object is important, randomly assign the importance duration
				if (covObjMap.get(i).getAttribute("is_important").equals("true")) {
					human.setDuration(RandomHelper.nextIntFromTo(5, 100)); 
				}
			} else {
				// initialization of an uncovered object
				angle = RandomHelper.nextIntFromTo(0, 360);
				Human human = new Human(i, space, grid, angle, humanSpeed, humanSeed);
				context.add(human);
			}
		}
		
		// set back to default seed for cameras
		RandomHelper.setSeed(seedOriginal);
		

		// Initialize camera position, messages, covered humans
		NodeList cameraListFromXML = scenario.getElementsByTagName("camera");
		
		assert (zombieCount == cameraListFromXML.getLength());

		for (int i = 0; i < cameraListFromXML.getLength(); i++) {
			Element cameraFromXML = (Element) cameraListFromXML.item(i);
			int id = Integer.parseInt(cameraFromXML.getAttribute("id"));
			double x = Double.parseDouble(cameraFromXML.getAttribute("x"));
			double y = Double.parseDouble(cameraFromXML.getAttribute("y"));
			
			Stream<Object> s1 = context.getObjectsAsStream(Camera.class);
			List<Object> cam = s1.filter(c -> ((Camera) c).getID() == id).collect(Collectors.toList());
			assert (cam.size() == 1);
			
			// initialize camera position
			space.moveTo(cam.get(0), new double[] { x, y });
			
			// initialize covered humans for the camera
			List<Object> newCoveredHumans = new ArrayList<>();
			NodeList objectList = cameraFromXML.getElementsByTagName("object");
			for (int j = 0; j < objectList.getLength(); j++) {
				Element obj = (Element) objectList.item(j);
				int idObj = Integer.parseInt(obj.getAttribute("id"));
				
				Stream<Object> ss = context.getObjectsAsStream(Human.class);
				List<Object> objCov = ss.filter(h -> ((Human) h).getID() == idObj).collect(Collectors.toList());
				newCoveredHumans.add(objCov.get(0));
			}
			((Camera) cam.get(0)).setCoveredHumans(newCoveredHumans);
			
			
			// initialize camera messages
			NodeList msgListFromXML = cameraFromXML.getElementsByTagName("message");
			for (int j = 0; j < msgListFromXML.getLength(); j++) {
				//System.out.println("Loading msg for camera" + i);
				Element msgFromXML = (Element) msgListFromXML.item(j);
				int msgTime = Integer.parseInt(msgFromXML.getAttribute("time"));
				int msgSender = Integer.parseInt(msgFromXML.getAttribute("camera_id"));
				int msgObj = Integer.parseInt(msgFromXML.getAttribute("object_id"));
				
				s1 = context.getObjectsAsStream(Camera.class);
				List<Object> sender = s1.filter(c -> ((Camera) c).getID() == msgSender).collect(Collectors.toList());
				assert sender.size() == 1;
				
				Stream<Object> sh = context.getObjectsAsStream(Human.class);
				List<Object> covObject = sh.filter(h -> ((Human) h).getID() == msgObj).collect(Collectors.toList());
				assert covObject.size() == 1;
				Human tmph = (Human) covObject.get(0);
				Camera tmp = (Camera) sender.get(0);
				
				((Camera) cam.get(0)).receiveMsg(new Message(msgTime, (Camera) (sender.get(0)), (Human) (covObject.get(0))));
			}
		}
		

		// Add edge with input weight to every pair of cameras
		Stream<Object> s2 = context.getObjectsAsStream(Camera.class);
		List<Object> camList = s2.collect(Collectors.toList());
		NodeList edgeListFromXML = scenario.getElementsByTagName("edge");

		assert ((camList.size() - 1) * camList.size() / 2 == edgeListFromXML.getLength());

		for (int i = 0; i < edgeListFromXML.getLength(); i++) {
			Element edgeFromXML = (Element) edgeListFromXML.item(i);
			int sourceID = Integer.parseInt(edgeFromXML.getAttribute("source_id"));
			int targetID = Integer.parseInt(edgeFromXML.getAttribute("target_id"));
			double strength = Double.parseDouble(edgeFromXML.getAttribute("strength"));

			s2 = context.getObjectsAsStream(Camera.class);
			Object camSource = s2.filter(c -> ((Camera) c).getID() == sourceID).collect(Collectors.toList()).get(0);
			s2 = context.getObjectsAsStream(Camera.class);
			Object camTarget = s2.filter(c -> ((Camera) c).getID() == targetID).collect(Collectors.toList()).get(0);

			net.addEdge(camSource, camTarget, strength);
		}


		// Initialize grid
		for (Object obj : context) {
			NdPoint pt = space.getLocation(obj);
			grid.moveTo(obj, (int) pt.getX(), (int) pt.getY());
		}
		
		// Scheduling 
		
		int startTime = 1 + Integer.parseInt(((Element) scenario.getElementsByTagName("scenario").item(0)).getAttribute("time"));
		int startTime2 = startTime + (6 - (startTime % 5)) % 5;
		
		Schedule schedule = (Schedule) RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters sp100 = ScheduleParameters.createRepeating(startTime, 1, 100);
		ScheduleParameters sp1Every5 = ScheduleParameters.createRepeating(startTime2, 5, 1); // this one is different
		ScheduleParameters spSecondLast = ScheduleParameters.createRepeating(startTime-1 , 1, ScheduleParameters.LAST_PRIORITY + 1);
		ScheduleParameters spFirst = ScheduleParameters.createRepeating(startTime, 1, ScheduleParameters.FIRST_PRIORITY);
		ScheduleParameters sp3 = ScheduleParameters.createRepeating(startTime, 1, 3);
		ScheduleParameters sp2 = ScheduleParameters.createRepeating(startTime, 1, 2);
		ScheduleParameters spLast = ScheduleParameters.createRepeating(startTime-1, 1, ScheduleParameters.LAST_PRIORITY);
		
		for (Object cam : camList) {
			schedule.schedule(sp100, cam, "step");
			schedule.schedule(sp1Every5, cam, "clearMsg");
			schedule.schedule(spSecondLast, cam, "printTrace");
		}
		
		Stream<Object> s3 = context.getObjectsAsStream(Human.class);
		List<Object> humList = s3.collect(Collectors.toList());
		for (Object hum : humList) {
			schedule.schedule(spFirst, hum, "run");
		}
		
		schedule.schedule(sp3, context, "evaporateNetwork");
		schedule.schedule(sp2, context, "strengthenNetwork");
		schedule.schedule(spLast, context, "collectTraceForEnv");


		// Redirecting printing to file
		boolean redirectOutput = true;
		if (redirectOutput) {
			
			File directory = new File("./trace");
			if (!directory.exists()) {
				directory.mkdir();
			}
			String outFile = params.getString("output_trace_path");

			File file = new File(outFile);
			if (!file.exists()) {
				file.getParentFile().mkdirs();
			}
			
			PrintStream stream;
			try {
				stream = new PrintStream(file);
				System.out.println("All \"System.out\" is directed to " + file.getAbsolutePath());
				System.out.flush();
				System.setOut(stream);
			} catch (FileNotFoundException e) {
				System.out.println("FAILED to dreict system output to file " + file.getAbsolutePath());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		

		return context;
	}

}