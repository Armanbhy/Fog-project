package org.fog.placement;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.List;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.application.Application;
import org.fog.application.AppModule;
import org.fog.placement.ValueComparator;


public class MyModulePlacement extends ModulePlacement {
	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected Map<Integer, Double> currentCpuLoad;
	
	/**
	 * Stores the current mapping of application modules to fog devices 
	 */
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
		setModuleInstanceCountMap(getCurrentModuleInstanceNum());
	}
	


	@Override
	protected void mapModules() { 
		Map<Integer,Double> NodesNS = new HashMap<Integer,Double>();
		Map<AppModule,Double> modulesToPlaceNS = new HashMap<AppModule,Double>();
		Application app = getApplication();
		for(AppModule module : app.getModules()){
			modulesToPlaceNS.put(module,module.getMips());
		}	
		for(FogDevice dev : getFogDevices()){
			NodesNS.put(dev.getId(),dev.getMips());
		}
		Map<FogDevice,List<AppModule>> moduleMap;
		moduleMap = placeModules(NodesNS,modulesToPlaceNS);
		for(FogDevice device : moduleMap.keySet()){
			for(AppModule module : moduleMap.get(device)){
				createModuleInstanceOnDevice(getApplication().getModuleByName(module.getName()), getFogDeviceById(device.getId()));
			}
		}
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<FogDevice,List<AppModule>> placeModules(Map<Integer,Double> NodesNS, Map<AppModule,Double> modulesToPlaceNS) {
		ValueComparator2 bvc2= new ValueComparator2(modulesToPlaceNS);
		ValueComparator bvc1= new ValueComparator(NodesNS);

		TreeMap<AppModule,Double> modulesToPlace = new TreeMap<AppModule,Double>(bvc2);
		TreeMap<Integer,Double> Nodes = new TreeMap<Integer,Double>(bvc1);

		modulesToPlace.putAll(modulesToPlaceNS); //sorts appModules in ascending order
		Nodes.putAll(NodesNS); //sorts nodes in ascending order

//		List<FogDevice> fdName = new ArrayList<FogDevice>();
//		List<Double> amMips = new ArrayList<Double>();
//		amName.addAll(modulesToPlace.keySet());
//		for(FogDevice dev:Nodes.) {
//			amMips.add(modulesToPlace.get(am));
//		}
		
		//

		 //
		Map<FogDevice,List<AppModule>> moduleMap = new HashMap<FogDevice,List<AppModule>>();
		List<AppModule> myAms = new ArrayList<AppModule>();
		List<Integer> myNodes = new ArrayList<Integer>();

		int low=0, high=Nodes.size()-1, start;
		for(Map.Entry<AppModule,Double> entry : modulesToPlace.entrySet()) {
			 AppModule am = entry.getKey();
			 myAms.add(am);
		}
		for(Map.Entry<Integer,Double> entry : Nodes.entrySet()) {
			Integer Node = entry.getKey();
			myNodes.add(Node);
		}
		for(start=0;start<modulesToPlace.size();start++) {
			int i=lowerbound(Nodes.keySet(),myAms.get(start),low,high);
			List<AppModule> myAms2 = new ArrayList<AppModule>();
			if(i!=-1) {
				myAms2.add(myAms.get(start));
				moduleMap.put(getFogDeviceById(myNodes.get(i)),myAms2);
				getFogDeviceById(myNodes.get(i)).setRam(getFogDeviceById(myNodes.get(i)).getRam()-myAms.get(start).getRam());
				getFogDeviceById(myNodes.get(i)).setMips(getFogDeviceById(myNodes.get(i)).getMips()-myAms.get(start).getMips());
				ValueComparator bvc= new ValueComparator(Nodes); //??????
				Nodes = new TreeMap<Integer,Double>(bvc); //??????
				low = i+1;
			}else {
				moduleMap.put(getFogDeviceById(myNodes.get(Nodes.size()-1)),myAms2);
			}
			modulesToPlace.remove(myAms.get(start));
		}

		return moduleMap;
	}
	
//	private Map<AppModule,Integer> getAmRam(AppModule am){
//		Application app = getApplication();
//		Map<AppModule,Integer> amRam = new HashMap<AppModule,Integer>();
//		for(AppModule module: app.getModules()) {
//			
//		}
//		return amRam;
//	}
	
	public int lowerbound(Set<Integer> fd,AppModule am, int low, int high) {
		int length = fd.size();
		int mid=(low+high)/2;
		List<Integer> fd1 = new ArrayList<Integer>(fd);
		while(true) {
			Integer x=fd1.get(mid);
			if (Compare(x,am)==1) {
				high=mid-1;
				if(high<low)
					return mid;
			} 
			else {
				low=mid+1;
				if(low>high)
					return ((mid<length-1)?mid+1:-1);
			}
			mid=(high+low)/2;
		}
	
	}
	public int Compare(Integer fdID,AppModule am) {
		if (getFogDeviceById(fdID).getMips()>= am.getMips() && getFogDeviceById(fdID).getRam()>=am.getRam())
			return 1;
		return -1;
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
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
		
}
