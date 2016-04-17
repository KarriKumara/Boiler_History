/**
 * Prosys OPC UA Java SDK
 *
 * Copyright (c) 2009-2010 Prosys PMS Ltd., <http://www.prosysopc.com>.
 * All rights reserved.
 */
package fi.opc.ua.server;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.opcfoundation.ua.builtintypes.DateTime;
import org.opcfoundation.ua.builtintypes.LocalizedText;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;
import org.opcfoundation.ua.core.Identifiers;

import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.nodes.UaNode;
import com.prosysopc.ua.nodes.UaNodeFactoryException;
import com.prosysopc.ua.nodes.UaObject;
import com.prosysopc.ua.nodes.UaObjectType;
import com.prosysopc.ua.nodes.UaType;
import com.prosysopc.ua.server.NodeManagerUaNode;
import com.prosysopc.ua.server.UaInstantiationException;
import com.prosysopc.ua.server.UaServer;
import com.prosysopc.ua.server.nodes.PlainVariable;
import com.prosysopc.ua.server.nodes.UaObjectNode;
import com.prosysopc.ua.server.nodes.UaObjectTypeNode;
import com.prosysopc.ua.server.nodes.UaVariableNode;
import com.prosysopc.ua.types.opcua.server.BaseEventTypeNode;
import com.prosysopc.ua.types.opcua.server.FolderTypeNode;

/**
 * A sample customized node manager, which actually just overrides the standard
 * NodeManagerUaNode and initializes the nodes for the demo.
 */
public class MyNodeManager extends NodeManagerUaNode {
	public static final String NAMESPACE = "http://www.prosysopc.com/OPCUA/SampleAddressSpace";
	private static final Logger logger = Logger.getLogger(MyNodeManager.class);
	//private static boolean stackTraceOnException;

	/**
	 * @param e
	 */
	/*
	private static void printException(Exception e) {
		if (stackTraceOnException)
			e.printStackTrace();
		else {
			println(e.toString());
			if (e.getCause() != null)
				println("Caused by: " + e.getCause());
		}
	}
	*/

	/**
	 * @param string
	 */
	protected static void println(String string) {
		System.out.println(string);
	}

	// private MyEventType myEvent;

	private FolderTypeNode myObjectsFolder;

	double dx = 1;

	final MyEventManagerListener myEventManagerListener = new MyEventManagerListener();

	/**
	 * Creates a new instance of MyNodeManager
	 *
	 * @param server
	 *            the server in which the node manager is created.
	 * @param namespaceUri
	 *            the namespace URI for the nodes
	 * @throws StatusException
	 *             if something goes wrong in the initialization
	 * @throws UaInstantiationException
	 */
	public MyNodeManager(UaServer server, String namespaceUri)
			throws StatusException, UaInstantiationException {
		super(server, namespaceUri);
	}

	/**
	 * @return
	 */
	public UaVariableNode[] getHistorizableVariables() {
		return new UaVariableNode[] { };
	}

	/**
	 * @return
	 */
	public UaObjectNode[] getHistorizableEvents() {
		return new UaObjectNode[] { myObjectsFolder };
	}

	/**
	 *
	 */
	public void sendEvent() {
		// If the type has TypeDefinitionId, you can use the class
		MyEventType ev = createEvent(MyEventType.class);
		ev.setMessage("MyEvent");
		ev.setMyVariable(new Random().nextInt());
		ev.setMyProperty("Property Value " + ev.getMyVariable());
		ev.triggerEvent(null);
	}

	private void createAddressSpace() throws StatusException,
			UaInstantiationException {
		// +++ My nodes +++

		int ns = getNamespaceIndex();

		// My Event Manager Listener
		this.getEventManager().setListener(myEventManagerListener);

		// UA types and folders which we will use
		final UaObject objectsFolder = getServer().getNodeManagerRoot()
				.getObjectsFolder();
		final UaType baseObjectType = getServer().getNodeManagerRoot().getType(
				Identifiers.BaseObjectType);
		final UaType baseDataVariableType = getServer().getNodeManagerRoot()
				.getType(Identifiers.BaseDataVariableType);

		// Folder for my objects
		final NodeId myObjectsFolderId = new NodeId(ns, "MyObjectsFolder");
		myObjectsFolder = createInstance(FolderTypeNode.class, "MyObjects",
				myObjectsFolderId);

		this.addNodeAndReference(objectsFolder, myObjectsFolder,
				Identifiers.Organizes);

		// My Device Type

		final NodeId myDeviceTypeId = new NodeId(ns, "MyDeviceType");
		UaObjectType myDeviceType = new UaObjectTypeNode(this, myDeviceTypeId,
				"MyDeviceType", Locale.ENGLISH);
		this.addNodeAndReference(baseObjectType, myDeviceType,
				Identifiers.HasSubtype);

//		createBoilerAddressSpace(ns, baseObjectType, 1);
		createMultiBoilerAddressSpace(ns, baseObjectType);
//		createTransformedBoilerAddressSpace(ns, baseObjectType);
	}

	private void createBoilerAddressSpace(int ns, UaType baseObjectType, int number) throws StatusException
	{
		//Boiler
		UaObjectType boilerType = createType(ns, "BoilerType", baseObjectType);
		UaObjectNode boiler = createNode(ns, "Boiler" + number, boilerType, myObjectsFolder);
		
		//Pipe1001
		UaObjectType pipeType = createType(ns, "PipeType", baseObjectType);
		UaObjectNode pipe1 = createNode(ns, "Pipe" + number + "001", pipeType, boiler);

		//FT1001
		UaObjectType ftType = createType(ns, "FTType", baseObjectType);
		UaObjectNode ft1 = createNode(ns, "FT" + number + "001", ftType, pipe1);

		//FT1001/DataItem
		PlainVariable<Double> ft1data = createVariable(ns, "DataItem", (double)1.0, Identifiers.Double, ft1);
		
		//Valve1001
		UaObjectType valveType = createType(ns, "ValveType", baseObjectType);
		UaObjectNode valve1 = createNode(ns, "Valve" + number + "001", valveType, pipe1);

		//Valve1001/DataItem
		PlainVariable<Double> valve1data = createVariable(ns, "DataItem", (double)2.0, Identifiers.Double, valve1);
		
		//Drum1001
		UaObjectType drumType = createType(ns, "DrumType", baseObjectType);
		UaObjectNode drum1 = createNode(ns, "Drum" + number + "001", drumType, boiler);

		//LI1001
		UaObjectType liType = createType(ns, "LIType", baseObjectType);
		UaObjectNode li1 = createNode(ns, "LI" + number + "001", liType, drum1);

		//LI1001/DataItem
		PlainVariable<Double> li1data = createVariable(ns, "DataItem", (double)3.0, Identifiers.Double, li1);

		//Pipe1001
		UaObjectNode pipe2 = createNode(ns, "Pipe" + number + "002", pipeType, boiler);

		//FT1001
		UaObjectNode ft2 = createNode(ns, "FT" + number + "002", ftType, pipe2);

		//FT1001/DataItem
		PlainVariable<Double> ft2data = createVariable(ns, "DataItem", (double)4.0, Identifiers.Double, ft2);
		
		//FC1001
		UaObjectType controllerType = createType(ns, "ControllerType", baseObjectType);
		UaObjectNode fc1 = createNode(ns, "FC" + number + "001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> fc1meas = createVariable(ns, "Measurement", (double)5.1, Identifiers.Double, fc1);
		PlainVariable<Double> fc1out = createVariable(ns, "ControlOut", (double)5.2, Identifiers.Double, fc1);
		PlainVariable<Double> fc1set = createVariable(ns, "SetPoint", (double)5.3, Identifiers.Double, fc1);

		//LC1001
		UaObjectNode lc1 = createNode(ns, "LC" + number + "001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> lc1meas = createVariable(ns, "Measurement", (double)6.1, Identifiers.Double, lc1);
		PlainVariable<Double> lc1out = createVariable(ns, "ControlOut", (double)6.2, Identifiers.Double, lc1);
		/*PlainVariable<Double> lc1set = */createVariable(ns, "SetPoint", (double)6.3, Identifiers.Double, lc1);

		//LC1001
		UaObjectNode cc1 = createNode(ns, "CC" + number + "001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> cc1in1 = createVariable(ns, "Input1", (double)7.1, Identifiers.Double, cc1);
		PlainVariable<Double> cc1in2 = createVariable(ns, "Input2", (double)7.2, Identifiers.Double, cc1);
		PlainVariable<Double> cc1in3 = createVariable(ns, "Input3", (double)7.3, Identifiers.Double, cc1);
		PlainVariable<Double> cc1out = createVariable(ns, "ControlOut", (double)7.3, Identifiers.Double, cc1);
		
		
		//**** OLD NODES ****//
		
		//TODO: CUSTOM REFERENCES
		pipe1.addReference(drum1, Identifiers.HasEffect, false);
		drum1.addReference(pipe2, Identifiers.HasEffect, false);
		
		ft1data.addReference(fc1meas, Identifiers.HasEffect, false);
		ft1data.addReference(cc1in2, Identifiers.HasEffect, false);
		li1data.addReference(lc1meas, Identifiers.HasEffect, false);
		ft2data.addReference(cc1in3, Identifiers.HasEffect, false);
		
		fc1out.addReference(valve1data, Identifiers.HasEffect, false);
		lc1out.addReference(cc1in1, Identifiers.HasEffect, false);
		cc1out.addReference(fc1set, Identifiers.HasEffect, false);
	}
	
	private void createMultiBoilerAddressSpace(int ns, UaType baseObjectType) throws StatusException {
		for(int i=1; i < 4; i++) {
			createBoilerAddressSpace(ns, baseObjectType, i);
		}
		
	}
	
	private void createTransformedBoilerAddressSpace(int ns, UaType baseObjectType) throws StatusException
	{
		//Boiler
		UaObjectType boilerType = createType(ns, "BoilerType", baseObjectType);
		UaObjectNode boiler = createNode(ns, "Boiler", boilerType, myObjectsFolder);
		
		//Pipe1001
//		UaObjectType pipeType = createType(ns, "PipeType", baseObjectType);
//		UaObjectNode pipe1 = createNode(ns, "Pipe1001", pipeType, boiler);

		//FT1001
//		UaObjectType ftType = createType(ns, "FTType", baseObjectType);
//		UaObjectNode ft1 = createNode(ns, "FT1001", ftType, pipe1);

		//FT1001/DataItem
		PlainVariable<Double> ft1data = createVariable(ns, "FT1001", (double)1.0, Identifiers.Double, boiler);
		
		//Valve1001
//		UaObjectType valveType = createType(ns, "ValveType", baseObjectType);
//		UaObjectNode valve1 = createNode(ns, "Valve1001", valveType, pipe1);

		//Valve1001/DataItem
//		PlainVariable<Double> valve1data = createVariable(ns, "DataItem", (double)2.0, Identifiers.Double, valve1);
		
		//Drum1001
//		UaObjectType drumType = createType(ns, "DrumType", baseObjectType);
//		UaObjectNode drum1 = createNode(ns, "Drum1001", drumType, boiler);

		//LI1001
//		UaObjectType liType = createType(ns, "LIType", baseObjectType);
//		UaObjectNode li1 = createNode(ns, "LI1001", liType, drum1);

		//LI1001/DataItem
		PlainVariable<Double> li1data = createVariable(ns, "LI1001", (double)3.0, Identifiers.Double, boiler);

		//Pipe1001
//		UaObjectNode pipe2 = createNode(ns, "Pipe1002", pipeType, boiler);

		//FT1001
//		UaObjectNode ft2 = createNode(ns, "FT1002", ftType, pipe2);

		//FT1001/DataItem
		PlainVariable<Double> ft2data = createVariable(ns, "FT1002", (double)4.0, Identifiers.Double, boiler);
		
		//FC1001
		UaObjectType controllerType = createType(ns, "ControllerType", baseObjectType);
		UaObjectNode fc1 = createNode(ns, "FC1001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> fc1meas = createVariable(ns, "Measurement", (double)5.1, Identifiers.Double, fc1);
		PlainVariable<Double> fc1out = createVariable(ns, "ControlOut", (double)5.2, Identifiers.Double, fc1);
		PlainVariable<Double> fc1set = createVariable(ns, "SetPoint", (double)5.3, Identifiers.Double, fc1);

		//LC1001
		UaObjectNode lc1 = createNode(ns, "LC1001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> lc1meas = createVariable(ns, "Measurement", (double)6.1, Identifiers.Double, lc1);
		PlainVariable<Double> lc1out = createVariable(ns, "ControlOut", (double)6.2, Identifiers.Double, lc1);
		/*PlainVariable<Double> lc1set = */createVariable(ns, "SetPoint", (double)6.3, Identifiers.Double, lc1);

		//LC1001
		UaObjectNode cc1 = createNode(ns, "CC1001", controllerType, boiler);

		//FT1001/variables
		PlainVariable<Double> cc1in1 = createVariable(ns, "Input1", (double)7.1, Identifiers.Double, cc1);
		PlainVariable<Double> cc1in2 = createVariable(ns, "Input2", (double)7.2, Identifiers.Double, cc1);
		PlainVariable<Double> cc1in3 = createVariable(ns, "Input3", (double)7.3, Identifiers.Double, cc1);
		PlainVariable<Double> cc1out = createVariable(ns, "ControlOut", (double)7.3, Identifiers.Double, cc1);
		
		
		//**** OLD NODES ****//
		
		//TODO: CUSTOM REFERENCE TYPES
		ft1data.addReference(li1data, Identifiers.HasEffect, false);
		li1data.addReference(ft2data, Identifiers.HasEffect, false);
		
		ft1data.addReference(fc1meas, Identifiers.HasEffect, false);
		ft1data.addReference(cc1in2, Identifiers.HasEffect, false);
		li1data.addReference(lc1meas, Identifiers.HasEffect, false);
		ft2data.addReference(cc1in3, Identifiers.HasEffect, false);
		
//		fc1out.addReference(valve1data, Identifiers.HasEffect, false);
		lc1out.addReference(cc1in1, Identifiers.HasEffect, false);
		cc1out.addReference(fc1set, Identifiers.HasEffect, false);
	}
	
	private UaObjectType createType(int ns, String name, UaType parent) throws StatusException
	{
		final NodeId id = new NodeId(ns, name + UUID.randomUUID());
		UaObjectType type = new UaObjectTypeNode(this, id, name, Locale.ENGLISH);
		this.addNodeAndReference(parent, type, Identifiers.HasSubtype);

		return type;
	}
	
	private UaObjectNode createNode(int ns, String name, UaObjectType type, UaObjectNode parent)
	{
		final NodeId id = new NodeId(ns, name + UUID.randomUUID());
		UaObjectNode node = new UaObjectNode(this, id, name, Locale.ENGLISH);
		node.setTypeDefinition(type);
		parent.addReference(node, Identifiers.HasComponent, false);
		
		return node;
	}
	
	private static boolean TESTNODE = false;
	private <T> PlainVariable<T> createVariable(int ns, String name, T value, NodeId dataTypeId, UaObjectNode parent)
	{
		NodeId id = new NodeId(ns, name + UUID.randomUUID());
		if(!TESTNODE && dataTypeId == Identifiers.Double) {
			TESTNODE = true;
			id = new NodeId(ns, "TESTNODE");
		}

		PlainVariable<T> variable = new PlainVariable<T>(this, id, name, LocalizedText.NO_LOCALE);
		
		variable.setDataTypeId(dataTypeId);
		
		variable.setTypeDefinitionId(Identifiers.BaseDataVariableType);
		parent.addComponent(variable);
		variable.setCurrentValue(value);

		return variable;
	}
	
	/**
	 * @return
	 */
	protected byte[] getNextUserEventId() {
		return myEventManagerListener.getNextUserEventId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.prosysopc.ua.server.NodeManagerUaNode#init()
	 */
	@Override
	protected void init() throws StatusException, UaNodeFactoryException {
		super.init();

		createAddressSpace();
	}

	/**
	 *
	 */
	// protected void initMyEvent() {
	// if (myEvent == null)
	// myEvent = new MyEventType(this);
	// }

	/**
	 * Send an event
	 *
	 * @throws StatusException
	 */
	// protected void sendEvent() throws StatusException {
	// // 1. send a standard SystemEventType here
	// SystemEventTypeNode newEvent = createEvent(SystemEventTypeNode.class);
	//
	// newEvent.setMessage("New event");
	// // Set the severity of the event between 1 and 1000
	// newEvent.setSeverity(1);
	// // By default the event is sent for the "Server" object. If you want to
	// // send it for some other object, use Source (or SourceNode), e.g.
	// // newEvent.setSource(myDevice);
	// triggerEvent(newEvent);
	//
	// // 2. Send our own event
	//
	// initMyEvent();
	// myEvent.setSource(myObjectsFolder);
	//
	// myEvent.setMyVariable(myEvent.getMyVariable() + 1);
	// myEvent.setMyProperty(DateTime.currentTime().toString());
	// triggerEvent(myEvent);
	// this.deleteNode(myEvent, true, true);
	// }

	void deleteNode(QualifiedName nodeName) throws StatusException {
		UaNode node = myObjectsFolder.getComponent(nodeName);
		if (node != null) {
			getServer().getNodeManagerRoot().beginModelChange();
			try {
				this.deleteNode(node, true, true);
			} finally {
				getServer().getNodeManagerRoot().endModelChange();
			}
		} else
			println("MyObjects does not contain a component with name "
					+ nodeName);
	}
}
