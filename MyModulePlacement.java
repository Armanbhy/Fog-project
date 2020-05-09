package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.application.Application;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;

public class MyModulePlacement extends ModulePlacement {
	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected String moduleToPlace;
	protected Map<Integer, Double> currentCpuLoad;
	
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;
	
	public MyModulePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
			Application application, ModuleMapping moduleMapping){
	this.setFogDevices(fogDevices);
	this.setApplication(application);
	this.setModuleMapping(moduleMapping);
	this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
	this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
	setSensors(sensors);
	setActuators(actuators);
	setCurrentCpuLoad(new HashMap<Integer, Double>());
	setCurrentModuleMap(new HashMap<Integer, List<String>>());
	setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
	setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
	for(FogDevice dev : getFogDevices()){
		
		getCurrentCpuLoad().put(dev.getId(), 0.0);
		getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
		getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
		getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
	}
	mapModules();
	}
	
	@Override
	protected void mapModules() { // end = line 138
		List<AppModule>placedModules = new ArrayList<AppModule>();
/*1*/	for(String deviceName : getModuleMapping().getModuleMapping().keySet()){
/*2*/		for(String moduleName : getModuleMapping().getModuleMapping().get(deviceName)){
				int deviceId = CloudSim.getEntityId(deviceName);
				AppModule appModule = getApplication().getModuleByName(moduleName);
				if(!getDeviceToModuleMap().containsKey(deviceId))
				{
					placedModules = new ArrayList<AppModule>();
					placedModules.add(appModule);
					getDeviceToModuleMap().put(deviceId, placedModules);
				}
				else
				{
					placedModules = getDeviceToModuleMap().get(deviceId);
					placedModules.add(appModule);
					getDeviceToModuleMap().put(deviceId, placedModules);
				}
			} /*e2*/
		} /*e1*/
		List<List<Integer>> leafToRootPaths = getLeafToRootPaths();
		
		for(List<Integer> path : leafToRootPaths){
			placeModulesInPath(path);
		}
		
		for(int deviceId : getCurrentModuleMap().keySet()){
			for(String module : getCurrentModuleMap().get(deviceId)){
				createModuleInstanceOnDevice(getApplication().getModuleByName(module), getFogDeviceById(deviceId));
			}
		}
	}
	private void placeModulesInPath(List<Integer> path) {
		List<AppModule>placedModules = new ArrayList<AppModule>();
/*3*/	for(FogDevice device:getFogDevices())
		{
			int deviceParent = -1;
			List<Integer>children = new ArrayList<Integer>();
			Map<Integer, Double>childDeadline = new HashMap<Integer, Double>();
			List<Integer> keys = new ArrayList<Integer>(childDeadline.keySet()); // keys = child IDs

			if(device.getLevel()==1)
			{
				if(!currentCpuLoad.containsKey(device.getId()))
					currentCpuLoad.put(device.getId(), 0.0);
				deviceParent = device.getParentId();
				for(FogDevice deviceChild:getFogDevices())
				{
					if(deviceChild.getParentId()==device.getId()){
						children.add(deviceChild.getId());}
				}
				for(int childId:children)
					childDeadline.put(childId,getApplication().getDeadlineInfo().get(childId).get(moduleToPlace));
			}


			List<String> modulesToPlace = getModulesToPlace(placedModules);
/*4*/		for(String moduleToPlace : modulesToPlace) {
				int baseMipsOfPlacingModule = (int)getApplication().getModuleByName(moduleToPlace).getMips();
/*5*/			for(int key:keys)
				{
					int max = Collections.max(keys);
					
					for(FogDevice d : getFogDevices()) {
						d.setMips(1000/max);
						double CpuLoad = currentCpuLoad.get(d.getId());
						AppModule appModule = getApplication().getModuleByName(moduleToPlace);
						int additionalMips = getApplication().getAdditionalMipsInfo().get(key).get(moduleToPlace);
						if((CpuLoad+device.getHost().getTotalMips()+additionalMips)<device.getMips())
						{
							CpuLoad = CpuLoad+baseMipsOfPlacingModule+additionalMips;
							currentCpuLoad.put(d.getId(), CpuLoad); //updates fog device mips info
							if(!getDeviceToModuleMap().containsKey(d.getId()))
							{
								placedModules = new ArrayList<AppModule>();
								placedModules.add(appModule);
								getDeviceToModuleMap().put(d.getId(), placedModules);
							}
							else
							{
								placedModules = getDeviceToModuleMap().get(device.getId());
								placedModules.add(appModule);
								getDeviceToModuleMap().put(device.getId(), placedModules);
							}
						}
						else
						{
							placedModules = getDeviceToModuleMap().get(deviceParent);
							placedModules.add(appModule);
							getDeviceToModuleMap().put(deviceParent, placedModules);
						}	
					}
					keys.remove(max);
				} /*e5*/
			} /*e4*/
		} /*e3*/
	} // end mapModules
	
	
	private List<String> getModulesToPlace(List<AppModule> placedModules){
		Application app = getApplication();
		List<String> modulesToPlace_1 = new ArrayList<String>();
		List<String> modulesToPlace = new ArrayList<String>();
		for(AppModule module : app.getModules()){
			if(!placedModules.contains(module.getName()))
				modulesToPlace_1.add(module.getName());
		}
		/*
		 * Filtering based on whether modules (to be placed) lower in physical topology are already placed
		 */
		for(String moduleName : modulesToPlace_1){
			boolean toBePlaced = true;
			
			for(AppEdge edge : app.getEdges()){
				//CHECK IF OUTGOING DOWN EDGES ARE PLACED
				if(edge.getSource().equals(moduleName) && edge.getDirection()==Tuple.DOWN && !placedModules.contains(edge.getDestination()))
					toBePlaced = false;
				//CHECK IF INCOMING UP EDGES ARE PLACED
				if(edge.getDestination().equals(moduleName) && edge.getDirection()==Tuple.UP && !placedModules.contains(edge.getSource()))
					toBePlaced = false;
			}
			if(toBePlaced)
				modulesToPlace.add(moduleName);
		}

		return modulesToPlace;
	}
	@SuppressWarnings("serial")
	protected List<List<Integer>> getPaths(final int fogDeviceId){
		FogDevice device = (FogDevice)CloudSim.getEntity(fogDeviceId); 
		if(device.getChildrenIds().size() == 0){		
			final List<Integer> path =  (new ArrayList<Integer>(){{add(fogDeviceId);}});
			List<List<Integer>> paths = (new ArrayList<List<Integer>>(){{add(path);}});
			return paths;
		}
		List<List<Integer>> paths = new ArrayList<List<Integer>>();
		for(int childId : device.getChildrenIds()){
			List<List<Integer>> childPaths = getPaths(childId);
			for(List<Integer> childPath : childPaths)
				childPath.add(fogDeviceId);
			paths.addAll(childPaths);
		}
		return paths;
	}
	
	protected List<List<Integer>> getLeafToRootPaths(){
		FogDevice cloud=null;
		for(FogDevice device : getFogDevices()){
			if(device.getName().equals("cloud"))
				cloud = device;
		}
		return getPaths(cloud.getId());
	}
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}
	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}
	
	public List<Sensor> getSensors() {
		return sensors;
	}
	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}
	public List<Actuator> getActuators() {
		return actuators;
	}
	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}
	// added from edgewards policy
	public Map<Integer, Double> getCurrentCpuLoad() {
		return currentCpuLoad;
	}

	public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
		this.currentCpuLoad= currentCpuLoad;
	}
	
	public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
		return currentModuleLoadMap;
	}

	public void setCurrentModuleLoadMap(
			Map<Integer, Map<String, Double>> currentModuleLoadMap) {
		this.currentModuleLoadMap = currentModuleLoadMap;
	}

	public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
		return currentModuleInstanceNum;
	}

	public void setCurrentModuleInstanceNum(
			Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
		this.currentModuleInstanceNum = currentModuleInstanceNum;
	}
	
	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
	}
		
}
