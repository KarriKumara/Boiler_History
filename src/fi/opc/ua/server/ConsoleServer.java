/**
 * Prosys OPC UA Java SDK
 *
 * Copyright (c) 2009-2012 Prosys PMS Ltd., <http://www.prosysopc.com>.
 * All rights reserved.
 */
package fi.opc.ua.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.DateTime;
import org.opcfoundation.ua.builtintypes.ExpandedNodeId;
import org.opcfoundation.ua.builtintypes.LocalizedText;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;
import org.opcfoundation.ua.builtintypes.StatusCode;
import org.opcfoundation.ua.builtintypes.UnsignedByte;
import org.opcfoundation.ua.builtintypes.UnsignedInteger;
import org.opcfoundation.ua.builtintypes.UnsignedLong;
import org.opcfoundation.ua.builtintypes.UnsignedShort;
import org.opcfoundation.ua.builtintypes.Variant;
import org.opcfoundation.ua.builtintypes.XmlElement;
import org.opcfoundation.ua.common.NamespaceTable;
import org.opcfoundation.ua.core.ApplicationDescription;
import org.opcfoundation.ua.core.EUInformation;
import org.opcfoundation.ua.core.Identifiers;
import org.opcfoundation.ua.core.Range;
import org.opcfoundation.ua.core.UserTokenPolicy;
import org.opcfoundation.ua.transport.security.HttpsSecurityPolicy;
import org.opcfoundation.ua.transport.security.KeyPair;
import org.opcfoundation.ua.transport.security.SecurityMode;
import org.opcfoundation.ua.utils.CertificateUtils;
import org.opcfoundation.ua.utils.EndpointUtil;
import org.xml.sax.SAXException;

import com.prosysopc.ua.ApplicationIdentity;
import com.prosysopc.ua.CertificateValidationListener;
import com.prosysopc.ua.ModelException;
import com.prosysopc.ua.PkiFileBasedCertificateValidator;
import com.prosysopc.ua.SecureIdentityException;
import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.UaAddress;
import com.prosysopc.ua.UaApplication.Protocol;
import com.prosysopc.ua.ValueRanks;
import com.prosysopc.ua.nodes.UaNode;
import com.prosysopc.ua.nodes.UaObject;
import com.prosysopc.ua.nodes.UaProperty;
import com.prosysopc.ua.nodes.UaType;
import com.prosysopc.ua.nodes.UaVariable;
import com.prosysopc.ua.server.FileNodeManager;
import com.prosysopc.ua.server.NodeBuilderConfiguration;
import com.prosysopc.ua.server.NodeBuilderException;
import com.prosysopc.ua.server.NodeManagerListener;
import com.prosysopc.ua.server.NodeManagerUaNode;
import com.prosysopc.ua.server.UaInstantiationException;
import com.prosysopc.ua.server.UaServer;
import com.prosysopc.ua.server.UaServerException;
import com.prosysopc.ua.server.UserValidator;
import com.prosysopc.ua.server.nodes.CacheVariable;
import com.prosysopc.ua.server.nodes.FileFolderType;
import com.prosysopc.ua.server.nodes.UaObjectNode;
import com.prosysopc.ua.server.nodes.UaVariableNode;
import com.prosysopc.ua.types.opcua.AnalogItemType;
import com.prosysopc.ua.types.opcua.DataItemType;
import com.prosysopc.ua.types.opcua.FolderType;
import com.prosysopc.ua.types.opcua.server.BuildInfoTypeNode;
import com.prosysopc.ua.types.opcua.server.DataItemTypeNode;
import com.prosysopc.ua.types.opcua.server.FolderTypeNode;

/**
 * A sample OPC UA server application.
 */
public class ConsoleServer {
	enum Action {
		/*ADD_NODE('a', "create boiler address space") {
			@Override
			ActionResult performAction(SampleConsoleServer s) {
				println("Creating boiler address space...");
				createBoilerAddressSpace(s);
				println("Done");
				return ActionResult.NOTHING;
			}
		},*/

		CLOSE('x', "close the server") {
			@Override
			ActionResult performAction(ConsoleServer s) {
				return ActionResult.CLOSE_SERVER;
			}
		},

		DELETE_NODE('d', "delete a node") {
			@Override
			ActionResult performAction(ConsoleServer s)
					throws StatusException {
				println("Enter the name of the node to delete (enter 'x' to cancel)");
				String input = readInput();
				if (!input.equals("x")) {
					QualifiedName nodeName = new QualifiedName(
							s.myNodeManager.getNamespaceIndex(), input);
					s.myNodeManager.deleteNode(nodeName);
				}
				return ActionResult.NOTHING;
			}
		},

		ENABLE_DIAGNOSTICS('D', "enable/disable server diagnostics") {
			@Override
			ActionResult performAction(ConsoleServer s)
					throws StatusException {
				final UaProperty enabledFlag = s.server.getNodeManagerRoot()
						.getServerData().getServerDiagnosticsNode()
						.getEnabledFlagNode();
				boolean newValue = !((Boolean) enabledFlag.getValue()
						.getValue().getValue());
				enabledFlag.setValue(Boolean.valueOf(newValue));
				println("Server Diagnostics "
						+ (newValue ? "Enabled" : "Disabled"));
				return ActionResult.NOTHING;
			}
		},

		SEND_EVENT('e', "send an event") {
			@Override
			ActionResult performAction(ConsoleServer s) {
				s.myNodeManager.sendEvent();
				return ActionResult.NOTHING;
			}
		};

		static Map<Character, Action> actionMap = new TreeMap<Character, Action>();
		static {
			for (Action a : Action.values())
				actionMap.put(a.getKey(), a);
		}

		public static Action parseAction(Character s) {
			return actionMap.get(s);
		}

		private final String description;
		private final Character key;

		Action(Character key, String description) {
			this.key = key;
			this.description = description;
		}

		public String getDescription() {
			return description;
		}

		/**
		 * @return the key
		 */
		public Character getKey() {
			return key;
		}

		/**
		 * Perform the Action
		 *
		 * @param s
		 *            the SampleConsoleServer instance (inner enums are static,
		 *            so this is a "trick" to access SampleConsoleServer's
		 *            fields from the inner enum)
		 * @return ActionResult
		 * @throws Exception
		 */
		abstract ActionResult performAction(ConsoleServer s)
				throws Exception;
	}

	enum ActionResult {
		CLOSE_SERVER, NOTHING;
	}

	/**
	 * Number of nodes to create for the Big Node Manager. This can be modified
	 * from the command line.
	 */
	private static Logger logger = Logger.getLogger(ConsoleServer.class);
	private static boolean stackTraceOnException = false;
	protected static String APP_NAME = "BoilerServer";

	protected static String discoveryServerUrl = "opc.tcp://localhost:4840";

	/**
	 * @param args
	 *            command line arguments for the application
	 * @throws StatusException
	 *             if the server address space creation fails
	 * @throws UaServerException
	 *             if the server initialization parameters are invalid
	 * @throws CertificateException
	 *             if the application certificate or private key, cannot be
	 *             loaded from the files due to certificate errors
	 */
	public static void main(String[] args) throws Exception {
		// Initialize log4j logging
		PropertyConfigurator.configureAndWatch(ConsoleServer.class
				.getResource("log.properties").getFile(), 5000);

		try {
			if (!parseCmdLineArgs(args)) {
				usage();
				return;
			}
		} catch (IllegalArgumentException e) {
			println("Invalid cmd line argument: " + e.getMessage());
			usage();
			return;
		}

		// *** Initialization and Start Up
		ConsoleServer sampleConsoleServer = new ConsoleServer();

		// Initialize the server
		sampleConsoleServer.initialize(52510, 52443, APP_NAME);

		// Create the address space
		sampleConsoleServer.createAddressSpace();

		// TCP Buffer size parameters - this may help with high traffic
		// situations.
		// See http://fasterdata.es.net/host-tuning/background/ for some hints
		// how to use it
		// UATcpServer.setReceiveBufferSize(700000);

		// Start the server, when you have finished your own initializations
		// This will allow connections from the clients
		// Start up the server (enabling or disabling diagnostics according to
		// the cmd line args)
		sampleConsoleServer.run(getUseDiags(args));
	}

	/**
	 * @param e
	 */
	public static void printException(Exception e) {
		if (stackTraceOnException)
			e.printStackTrace();
		else {
			println(e.toString());
			if (e.getCause() != null)
				println("Caused by: " + e.getCause());
		}
	}

	/**
	 * @param string
	 */
	public static void println(String string) {
		System.out.println(string);
	}

	/**
	 * @return
	 */
	private static Action readAction() {
		return Action.parseAction(readInput().charAt(0));
	}

	/**
	 * @return
	 */
	private static String readInput() {
		BufferedReader stdin = new BufferedReader(new InputStreamReader(
				System.in));
		String s = null;
		do
			try {
				s = stdin.readLine();
			} catch (IOException e) {
				printException(e);
			}
		while ((s == null) || (s.length() == 0));
		return s;
	}

	/**
	 * Check if diagnostics is enabled from the command line
	 *
	 * @param args
	 * @return
	 */
	protected static boolean getUseDiags(String[] args) {
		for (String arg : args)
			if (arg.equals("-enablesessiondiags"))
				return true;
		return false;
	}

	/**
	 * Parse Command line arguments. Expected options:
	 * <UL>
	 * <LI>-d connect to a discovery server instead of a normal server
	 * <LI>-t show stack trace with exceptions
	 * <LI>-n do not prompt for the server URI, if it is not specified
	 * </UL>
	 *
	 * Also expects to get the serverUri - if not, it is prompted (unless -n
	 * given)
	 *
	 * @param args
	 *            the arguments
	 * @return
	 */
	protected static boolean parseCmdLineArgs(String[] args)
			throws IllegalArgumentException {
		int i = 0;
		while ((args.length > i)
				&& ((args[i].startsWith("-") || args[i].startsWith("/")))) {
			if (args[i].equals("-t"))
				stackTraceOnException = true;
			else if (args[i].equals("-k"))
				CertificateUtils.setKeySize(Integer.parseInt(args[++i]));
			else if (args[i].equals("-d"))
				discoveryServerUrl = args[++i];
			else if (args[i].equals("-d-"))
				discoveryServerUrl = "";
			else if (args[i].equals("-?"))
				return false;
			else
				throw new IllegalArgumentException(args[i]);
			i++;
		}
		return true;
	}

	/**
	 *
	 */
	protected static void usage() {
		println("Usage: " + APP_NAME + " [-b] [-t] [serverUri]");
		println("   -k keySize Define the size of the public key of the application certificate (default 1024; other valid values 2048, 4096)");
		println("   -d url     Define the DiscoveryServerUrl to register the application to");
		println("   -d-        Define that the application should not be registered to a DiscoveryServer");
		println("   -t         Output stack trace for errors");
		println("   -?         Show this help text");
		println("");
	}

	static void printMenu() {
		println("");
		println("");
		println("");
		System.out
				.println("-------------------------------------------------------");
		for (Entry<Character, Action> a : Action.actionMap.entrySet())
			println("- Enter " + a.getKey() + " to "
					+ a.getValue().getDescription());
	}

	private FolderType analogItemArrayFolder;
	private FolderType analogItemFolder;
	private FolderType dataItemFolder;
	private FolderType deepFolder;
	private FileNodeManager fileNodeManager;
	private FolderType staticArrayVariableFolder;
	private FolderType staticVariableFolder;
	protected int complianceNamespaceIndex;
	protected NodeManagerUaNode complianceNodeManager;
	protected MyHistorian myHistorian = new MyHistorian();
	protected MyNodeManager myNodeManager;
	protected NodeManagerListener myNodeManagerListener = new MyNodeManagerListener();
	protected UaServer server;
	protected final UserValidator userValidator = new MyUserValidator();

	protected final CertificateValidationListener validationListener = new MyCertificateValidationListener();

	public UaServer getServer() {
		return server;
	}

	private void addDeepObject(UaNode parent, int depth, int maxDepth) {
		if (depth <= maxDepth) {
			final String name = String.format("DeepObject%02d", depth);
			UaObjectNode newObject = new UaObjectNode(complianceNodeManager,
					new NodeId(complianceNamespaceIndex, name), name,
					Locale.ENGLISH);
			try {
				complianceNodeManager.addNodeAndReference(parent, newObject,
						Identifiers.Organizes);
			} catch (StatusException e) {
			}
			addDeepObject(newObject, depth + 1, maxDepth);
		}
	}

	private AnalogItemType createAnalogItem(String dataTypeName,
			NodeId dataTypeId, Object initialValue, UaNode folder)
					throws NodeBuilderException, StatusException {

		// Configure the optional nodes using a NodeBuilderConfiguration
		NodeBuilderConfiguration conf = new NodeBuilderConfiguration();

		// You can use NodeIds to define Optional nodes (good for standard UA
		// nodes as they always have namespace index of 0)
		conf.addOptional(Identifiers.AnalogItemType_EngineeringUnits);

		// You can also use ExpandedNodeIds with NamespaceUris if you don't know
		// the namespace index.
		conf.addOptional(new ExpandedNodeId(NamespaceTable.OPCUA_NAMESPACE,
				Identifiers.AnalogItemType_InstrumentRange.getValue()));

		// You can also use the BrowsePath from the type if you like (the type's
		// BrowseName is not included in the path, so this configuration will
		// apply to any type which has the same path)
		// You can use Strings for 0 namespace index, QualifiedNames for 1-step
		// paths and BrowsePaths for full paths
		// Each type interface has constants for it's structure (1-step deep)
		conf.addOptional(AnalogItemType.DEFINITION);

		// Use the NodeBuilder to create the node
		final AnalogItemType node = complianceNodeManager
				.createNodeBuilder(AnalogItemType.class, conf)
				.setName(dataTypeName + "AnalogItem").build();

		node.setDefinition("Sample AnalogItem of type " + dataTypeName);
		node.setDataTypeId(dataTypeId);
		node.setValueRank(ValueRanks.Scalar);

		node.setEngineeringUnits(new EUInformation("http://www.example.com", 3,
				new LocalizedText("kg", LocalizedText.NO_LOCALE),
				new LocalizedText("kilogram", Locale.ENGLISH)));

		node.setEuRange(new Range(0.0, 1000.0));
		node.setValue(new DataValue(new Variant(initialValue), StatusCode.GOOD,
				DateTime.currentTime(), DateTime.currentTime()));
		folder.addReference(node, Identifiers.HasComponent, false);
		return node;
	}

	private AnalogItemType createAnalogItemArray(String dataTypeName,
			NodeId dataType, Object initialValue, UaNode folder)
					throws StatusException, NodeBuilderException {
		AnalogItemType node = createAnalogItem(dataTypeName + "Array",
				dataType, initialValue, folder);
		node.setValueRank(ValueRanks.OneDimension);
		node.setArrayDimensions(new UnsignedInteger[] { UnsignedInteger
				.valueOf(Array.getLength(initialValue)) });
		return node;
	}

	/**
	 * @throws NodeBuilderException
	 *
	 */
	private void createComplianceNodes() throws NodeBuilderException {
		try {
			// My Node Manager
			complianceNodeManager = new NodeManagerUaNode(server,
					"http://www.prosysopc.com/OPCUA/ComplianceNodes");

			complianceNamespaceIndex = complianceNodeManager
					.getNamespaceIndex();

			// UA types and folders which we will use
			final UaObject objectsFolder = server.getNodeManagerRoot().getObjectsFolder();

			final NodeId staticDataFolderId = new NodeId(complianceNamespaceIndex, "StaticData");
			FolderType staticDataFolder = complianceNodeManager.createInstance(FolderType.class, "StaticData", staticDataFolderId);

			objectsFolder.addReference(staticDataFolder, Identifiers.Organizes, false);

			// Folder for static test variables
			final NodeId staticVariableFolderId = new NodeId(complianceNamespaceIndex, "StaticVariablesFolder");
			staticVariableFolder = complianceNodeManager.createInstance(FolderTypeNode.class, "StaticVariables", staticVariableFolderId);

			complianceNodeManager.addNodeAndReference(staticDataFolder,staticVariableFolder, Identifiers.Organizes);

			createStaticVariable("Boolean", Identifiers.Boolean, true);
			createStaticVariable("Byte", Identifiers.Byte, UnsignedByte.valueOf(0));
			createStaticVariable("ByteString", Identifiers.ByteString, new byte[] { (byte) 0 });
			createStaticVariable("DateTime", Identifiers.DateTime, DateTime.currentTime());
			createStaticVariable("Double", Identifiers.Double, (double) 0);
			createStaticVariable("Float", Identifiers.Float, (float) 0);
			createStaticVariable("GUID", Identifiers.Guid, UUID.randomUUID());
			createStaticVariable("Int16", Identifiers.Int16, (short) 0);
			createStaticVariable("Int32", Identifiers.Int32, 0);
			createStaticVariable("Int64", Identifiers.Int64, (long) 0);
			createStaticVariable("SByte", Identifiers.SByte, (byte) 0);
			createStaticVariable("String", Identifiers.String, "testString");
			createStaticVariable("UInt16", Identifiers.UInt16, UnsignedShort.valueOf(0));
			createStaticVariable("UInt32", Identifiers.UInt32, UnsignedInteger.valueOf(0));
			createStaticVariable("UInt64", Identifiers.UInt64, UnsignedLong.valueOf(0));
			createStaticVariable("XmlElement", Identifiers.XmlElement, new XmlElement("<testElement />"));

			// Folder for static test array variables
			final NodeId staticArrayVariableFolderId = new NodeId(complianceNamespaceIndex, "StaticArrayVariablesFolder");
			staticArrayVariableFolder = complianceNodeManager.createInstance(FolderTypeNode.class, "StaticArrayVariables", staticArrayVariableFolderId);

			staticDataFolder.addReference(staticArrayVariableFolder, Identifiers.Organizes, false);

			createStaticArrayVariable("BooleanArray", Identifiers.Boolean, new Boolean[] { true, false, true, false, false });
			createStaticArrayVariable(
					"ByteArray",
					Identifiers.Byte,
					new UnsignedByte[] { UnsignedByte.valueOf(1),
							UnsignedByte.valueOf(2), UnsignedByte.valueOf(3),
							UnsignedByte.valueOf(4), UnsignedByte.valueOf(5) });
			createStaticArrayVariable("ByteStringArray",
					Identifiers.ByteString, new byte[][] {
							new byte[] { (byte) 1, (byte) 2, (byte) 3 },
							new byte[] { (byte) 2, (byte) 3, (byte) 4 },
							new byte[] { (byte) 3, (byte) 4, (byte) 5 },
							new byte[] { (byte) 4, (byte) 5, (byte) 6 },
							new byte[] { (byte) 5, (byte) 6, (byte) 7 } });
			createStaticArrayVariable(
					"DateTimeArray",
					Identifiers.DateTime,
					new DateTime[] { DateTime.currentTime(),
							DateTime.currentTime(), DateTime.currentTime(),
							DateTime.currentTime(), DateTime.currentTime() });
			createStaticArrayVariable("DoubleArray", Identifiers.Double,
					new Double[] { (double) 1, (double) 2, (double) 3,
							(double) 4, (double) 5 });
			createStaticArrayVariable("FloatArray", Identifiers.Float,
					new Float[] { (float) 1, (float) 2, (float) 3, (float) 4,
							(float) 5 });
			createStaticArrayVariable(
					"GUIDArray",
					Identifiers.Guid,
					new UUID[] { UUID.randomUUID(), UUID.randomUUID(),
							UUID.randomUUID(), UUID.randomUUID(),
							UUID.randomUUID() });
			createStaticArrayVariable("Int16Array", Identifiers.Int16,
					new Short[] { (short) 1, (short) 2, (short) 3, (short) 4,
							(short) 5 });
			createStaticArrayVariable("Int32Array", Identifiers.Int32,
					new Integer[] { 1, 2, 3, 4, 5 });
			createStaticArrayVariable("Int64Array", Identifiers.Int64,
					new Long[] { (long) 1, (long) 2, (long) 3, (long) 4,
							(long) 5 });
			createStaticArrayVariable("SByteArray", Identifiers.SByte,
					new Byte[] { (byte) 0, (byte) 15, (byte) 255, (byte) 15,
							(byte) 0 });
			createStaticArrayVariable("StringArray", Identifiers.String,
					new String[] { "testString1", "testString2", "testString3",
							"testString4", "testString5" });
			createStaticArrayVariable(
					"UInt16Array",
					Identifiers.UInt16,
					new UnsignedShort[] { UnsignedShort.valueOf(1),
							UnsignedShort.valueOf(2), UnsignedShort.valueOf(3),
							UnsignedShort.valueOf(4), UnsignedShort.valueOf(5) });
			createStaticArrayVariable(
					"UInt32Array",
					Identifiers.UInt32,
					new UnsignedInteger[] { UnsignedInteger.valueOf(1),
							UnsignedInteger.valueOf(2),
							UnsignedInteger.valueOf(3),
							UnsignedInteger.valueOf(4),
							UnsignedInteger.valueOf(5) });
			createStaticArrayVariable(
					"UInt64Array",
					Identifiers.UInt64,
					new UnsignedLong[] { UnsignedLong.valueOf(1),
							UnsignedLong.valueOf(2), UnsignedLong.valueOf(3),
							UnsignedLong.valueOf(4), UnsignedLong.valueOf(5) });
			createStaticArrayVariable("XmlElementArray",
					Identifiers.XmlElement, new XmlElement[] {
							new XmlElement("<testElement1 />"),
							new XmlElement("<testElement2 />"),
							new XmlElement("<testElement3 />"),
							new XmlElement("<testElement4 />"),
							new XmlElement("<testElement5 />") });

			// Folder for DataItem test variables
			final NodeId dataItemFolderId = new NodeId(
					complianceNamespaceIndex, "DataItemsFolder");
			dataItemFolder = complianceNodeManager.createFolder("DataItems",
					dataItemFolderId);
			staticDataFolder.addReference(dataItemFolder,
					Identifiers.Organizes, false);

			// createDataItem("Boolean", Identifiers.Boolean, true);
			createDataItem("Byte", Identifiers.Byte, UnsignedByte.valueOf(0));
			// createDataItem("ByteString", Identifiers.ByteString,
			// new byte[] { (byte) 0 });
			createDataItem("DateTime", Identifiers.DateTime,
					DateTime.currentTime());
			createDataItem("Double", Identifiers.Double, (double) 0);
			createDataItem("Float", Identifiers.Float, (float) 0);
			// createDataItem("GUID", Identifiers.Guid, UUID.randomUUID());
			createDataItem("Int16", Identifiers.Int16, (short) 0);
			createDataItem("Int32", Identifiers.Int32, 0);
			createDataItem("Int64", Identifiers.Int64, (long) 0);
			createDataItem("SByte", Identifiers.SByte, (byte) 0);
			createDataItem("String", Identifiers.String, "testString");
			createDataItem("UInt16", Identifiers.UInt16,
					UnsignedShort.valueOf(0));
			createDataItem("UInt32", Identifiers.UInt32,
					UnsignedInteger.valueOf(0));
			createDataItem("UInt64", Identifiers.UInt64,
					UnsignedLong.valueOf(0));

			// Folder for DataItem test variables
			final NodeId analogItemFolderId = new NodeId(
					complianceNamespaceIndex, "AnalogItemsFolder");
			analogItemFolder = complianceNodeManager.createFolder(
					"AnalogItems", analogItemFolderId);
			staticDataFolder.addReference(analogItemFolder,
					Identifiers.Organizes, false);

			createAnalogItem("Byte", Identifiers.Byte, UnsignedByte.valueOf(0),
					analogItemFolder);
			createAnalogItem("Double", Identifiers.Double, (double) 0,
					analogItemFolder);
			createAnalogItem("Float", Identifiers.Float, (float) 0,
					analogItemFolder);
			createAnalogItem("Int16", Identifiers.Int16, (short) 0,
					analogItemFolder);
			createAnalogItem("Int32", Identifiers.Int32, 0, analogItemFolder);
			createAnalogItem("Int64", Identifiers.Int64, (long) 0,
					analogItemFolder);
			createAnalogItem("SByte", Identifiers.SByte, (byte) 0,
					analogItemFolder);
			createAnalogItem("UInt16", Identifiers.UInt16,
					UnsignedShort.valueOf(0), analogItemFolder);
			createAnalogItem("UInt32", Identifiers.UInt32,
					UnsignedInteger.valueOf(0), analogItemFolder);
			createAnalogItem("UInt64", Identifiers.UInt64,
					UnsignedLong.valueOf(0), analogItemFolder);

			// Folder for static test array variables
			final NodeId analogItemArrayFolderId = new NodeId(
					complianceNamespaceIndex, "AnalogItemArrayFolder");
			analogItemArrayFolder = complianceNodeManager.createFolder(
					"AnalogItemArrays", analogItemArrayFolderId);
			staticDataFolder.addReference(analogItemArrayFolder,
					Identifiers.Organizes, false);

			createAnalogItemArray("Double", Identifiers.Double,
					new Double[] { (double) 1, (double) 2, (double) 3,
							(double) 4, (double) 5 }, analogItemArrayFolder);
			createAnalogItemArray("Float", Identifiers.Float, new Float[] {
					(float) 1, (float) 2, (float) 3, (float) 4, (float) 5 },
					analogItemArrayFolder);
			createAnalogItemArray("Int16", Identifiers.Int16, new Short[] {
					(short) 1, (short) 2, (short) 3, (short) 4, (short) 5 },
					analogItemArrayFolder);
			createAnalogItemArray("Int32", Identifiers.Int32, new Integer[] {
					1, 2, 3, 4, 5 }, analogItemArrayFolder);
			createAnalogItemArray(
					"UInt16",
					Identifiers.UInt16,
					new UnsignedShort[] { UnsignedShort.valueOf(1),
							UnsignedShort.valueOf(2), UnsignedShort.valueOf(3),
							UnsignedShort.valueOf(4), UnsignedShort.valueOf(5) },
					analogItemArrayFolder);
			createAnalogItemArray(
					"UInt32",
					Identifiers.UInt32,
					new UnsignedInteger[] { UnsignedInteger.valueOf(1),
							UnsignedInteger.valueOf(2),
							UnsignedInteger.valueOf(3),
							UnsignedInteger.valueOf(4),
							UnsignedInteger.valueOf(5) }, analogItemArrayFolder);

			// Folder for deep object chain
			final NodeId deepFolderId = new NodeId(complianceNamespaceIndex,
					"DeepFolder");
			deepFolder = complianceNodeManager.createFolder("DeepFolder",
					deepFolderId);
			staticDataFolder.addReference(deepFolder, Identifiers.Organizes,
					false);

			addDeepObject(deepFolder, 1, 20);

			// / COMPLIANCE TEST NODES END HERE ///

			logger.info("Compliance address space created.");
		} catch (StatusException e) {
			logger.error("Error occurred with creating compliance nodes: ", e);
		} catch (UaInstantiationException e) {
			logger.error("Error occurred with creating compliance nodes: ", e);
		}
	}

	private void createDataItem(String dataTypeName, NodeId dataTypeId,
			Object initialValue) throws StatusException,
			UaInstantiationException {
		DataItemType node = complianceNodeManager.createInstance(
				DataItemTypeNode.class, dataTypeName + "DataItem");

		node.setDataTypeId(dataTypeId);
		node.setValue(new DataValue(new Variant(initialValue), StatusCode.GOOD,
				new DateTime(), new DateTime()));
		dataItemFolder.addReference(node, Identifiers.HasComponent, false);
	}

	/**
	 * @throws StatusException
	 *
	 */
	private void createFileNodeManager() throws StatusException {
		fileNodeManager = new FileNodeManager(getServer(),
				"http://prosysopc.com/OPCUA/FileTransfer", "Files");
		getServer()
		.getNodeManagerRoot()
		.getObjectsFolder()
		.addReference(fileNodeManager.getRootFolder(),
				Identifiers.Organizes, false);
		FileFolderType folder = fileNodeManager.addFolder("Folder");
		folder.setFilter("*");
	}

	private void createStaticArrayVariable(String dataTypeName,
			NodeId dataType, Object initialValue) throws StatusException {
		final NodeId nodeId = new NodeId(complianceNamespaceIndex, dataTypeName);
		UaType type = server.getNodeManagerRoot().getType(dataType);
		UaVariableNode node = new CacheVariable(complianceNodeManager, nodeId,
				dataTypeName, Locale.ENGLISH);
		node.setDataType(type);
		node.setTypeDefinition(type);
		node.setValueRank(ValueRanks.OneDimension);
		node.setArrayDimensions(new UnsignedInteger[] { UnsignedInteger
				.valueOf(Array.getLength(initialValue)) });

		node.setValue(new DataValue(new Variant(initialValue), StatusCode.GOOD,
				new DateTime(), new DateTime()));
		staticArrayVariableFolder.addReference(node, Identifiers.HasComponent,
				false);
	}

	/**
	 * Create a sample address space with a new folder, a device object, a level
	 * variable, and an alarm condition.
	 * <p>
	 * The method demonstrates the basic means to create the nodes and
	 * references into the address space.
	 * <p>
	 * Simulation of the level measurement is defined in
	 * {@link #startSimulation()}
	 *
	 * @throws StatusException
	 *             if the referred type nodes are not found from the address
	 *             space
	 * @throws UaInstantiationException
	 * @throws NodeBuilderException
	 * @throws URISyntaxException
	 * @throws ModelException
	 * @throws IOException
	 * @throws SAXException
	 *
	 */
	protected void createAddressSpace() throws StatusException,
			UaInstantiationException, NodeBuilderException {
		// Load the standard information models
		loadInformationModels();

		// My Node Manager
		myNodeManager = new MyNodeManager(server, MyNodeManager.NAMESPACE);

		myNodeManager.addListener(myNodeManagerListener);

		// My I/O Manager Listener
		myNodeManager.getIoManager().addListeners(new MyIoManagerListener());

		// My HistoryManager
		myNodeManager.getHistoryManager().setListener(myHistorian);

		// More specific nodes to enable OPC UA compliance testing of more
		// advanced features
		createComplianceNodes();

		createFileNodeManager();

		logger.info("Address space created.");
	}

	protected UaVariableNode createStaticVariable(String dataTypeName,
			NodeId dataType, Object initialValue) throws StatusException {
		final NodeId nodeId = new NodeId(complianceNamespaceIndex, dataTypeName);
		UaType type = server.getNodeManagerRoot().getType(dataType);
		UaVariableNode node = new CacheVariable(complianceNodeManager, nodeId,
				dataTypeName, Locale.ENGLISH);
		node.setDataType(type);
		node.setValue(new DataValue(new Variant(initialValue), StatusCode.GOOD,
				new DateTime(), new DateTime()));
		staticVariableFolder
				.addReference(node, Identifiers.HasComponent, false);
		return node;
	}

	/**
	 * Initialize the information to the Server BuildInfo structure
	 */
	protected void initBuildInfo() {
		// Initialize BuildInfo - using the version info from the SDK
		// You should replace this with your own build information

		final BuildInfoTypeNode buildInfo = server.getNodeManagerRoot()
				.getServerData().getServerStatusNode().getBuildInfoNode();

		// Fetch version information from the package manifest
		final Package sdkPackage = UaServer.class.getPackage();
		final String implementationVersion = sdkPackage.getImplementationVersion();
		if (implementationVersion != null) {
			int splitIndex = implementationVersion.lastIndexOf(".");
			final String softwareVersion = implementationVersion.substring(0, splitIndex);
			String buildNumber = implementationVersion.substring(splitIndex + 1);

			buildInfo.setManufacturerName(sdkPackage.getImplementationVendor());
			buildInfo.setSoftwareVersion(softwareVersion);
			buildInfo.setBuildNumber(buildNumber);

		}

		final URL classFile = UaServer.class.getResource("/com/prosysopc/ua/samples/server/SampleConsoleServer.class");
		if (classFile != null) {
			final File mfFile = new File(classFile.getFile());
			GregorianCalendar c = new GregorianCalendar();
			c.setTimeInMillis(mfFile.lastModified());
			buildInfo.setBuildDate(new DateTime(c));
		}
	}

	/**
	 *
	 */
	protected void initHistory() {
		for (UaVariableNode v : myNodeManager.getHistorizableVariables())
			myHistorian.addVariableHistory(v);
		for (UaObjectNode o : myNodeManager.getHistorizableEvents())
			myHistorian.addEventHistory(o);
	}

	protected void initialize(int port, int httpsPort, String applicationName)
			throws SecureIdentityException, IOException, UaServerException {

		// *** Create the server
		server = new UaServer();

		// Use PKI files to keep track of the trusted and rejected client
		// certificates...
		final PkiFileBasedCertificateValidator validator = new PkiFileBasedCertificateValidator();
		server.setCertificateValidator(validator);
		// ...and react to validation results with a custom handler
		validator.setValidationListener(validationListener);

		// *** Application Description is sent to the clients
		ApplicationDescription appDescription = new ApplicationDescription();
		appDescription.setApplicationName(new LocalizedText(applicationName, Locale.ENGLISH));
		// 'localhost' (all lower case) in the URI is converted to the actual
		// host name of the computer in which the application is run
		appDescription.setApplicationUri("urn:localhost:OPCUA:"
				+ applicationName);
		appDescription.setProductUri("urn:prosysopc.com:OPCUA:"
				+ applicationName);

		// *** Server Endpoints
		// TCP Port number for the UA Binary protocol
		server.setPort(Protocol.OpcTcp, port);
		// TCP Port for the HTTPS protocol
		server.setPort(Protocol.Https, httpsPort);

		// optional server name part of the URI (default for all protocols)
		server.setServerName("OPCUA/" + applicationName);

		// Optionally restrict the InetAddresses to which the server is bound.
		// You may also specify the addresses for each Protocol.
		// This is the default:
		server.setBindAddresses(EndpointUtil.getInetAddresses());

		// *** Certificates

		File privatePath = new File(validator.getBaseDir(), "private");

		// Define a certificate for a Certificate Authority (CA) which is used
		// to issue the keys. Especially
		// the HTTPS certificate should be signed by a CA certificate, in order
		// to make the .NET applications trust it.
		//
		// If you have a real CA, you should use that instead of this sample CA
		// and create the keys with it.
		// Here we use the IssuerCertificate only to sign the HTTPS certificate
		// (below) and not the Application Instance Certificate.
		KeyPair issuerCertificate = ApplicationIdentity.loadOrCreateIssuerCertificate(
				"ProsysSampleCA",
				privatePath,
				"opcua",
				3650,
				false);

		// If you wish to use big certificates (4096 bits), you will need to
		// define two certificates for your application, since to interoperate
		// with old applications, you will also need to use a small certificate
		// (up to 2048 bits).

		// Also, 4096 bits can only be used with Basic256Sha256 security
		// profile, which is currently not enabled by default, so we will also
		// leave the the keySizes array as null. In that case, the default key
		// size defined by CertificateUtils.getKeySize() is used.
		int[] keySizes = null;

		// Use 0 to use the default keySize and default file names as before
		// (for other values the file names will include the key size).
		// keySizes = new int[] { 0, 4096 };

		// *** Application Identity

		// Define the Server application identity, including the Application
		// Instance Certificate (but don't sign it with the issuerCertificate as
		// explained above).
		final ApplicationIdentity identity = ApplicationIdentity
				.loadOrCreateCertificate(appDescription, "Sample Organisation",
				/* Private Key Password */"opcua",
				/* Key File Path */privatePath,
				/* Issuer Certificate & Private Key */null,
				/* Key Sizes for instance certificates to create */keySizes,
				/* Enable renewing the certificate */true);

		// Create the HTTPS certificate bound to the hostname.
		// The HTTPS certificate must be created, if you enable HTTPS.
		String hostName = ApplicationIdentity.getActualHostName();
		identity.setHttpsCertificate(ApplicationIdentity
				.loadOrCreateHttpsCertificate(appDescription, hostName,
						"opcua", issuerCertificate, privatePath, true));

		server.setApplicationIdentity(identity);

		// *** Security settings
		// Define the security modes to support for the Binary protocol -
		// ALL is the default
		server.setSecurityModes(SecurityMode.ALL);
		// The TLS security policies to use for HTTPS
		server.getHttpsSettings().setHttpsSecurityPolicies(
				HttpsSecurityPolicy.ALL);

		// Number of threads to reserve for the HTTPS server, default is 10
		// server.setHttpsWorkerThreadCount(10);

		// Define a custom certificate validator for the HTTPS certificates
		server.getHttpsSettings().setCertificateValidator(validator);
		// client.getHttpsSettings().setCertificateValidator(...);

		// Or define just a validation rule to check the hostname defined for
		// the certificate; ALLOW_ALL_HOSTNAME_VERIFIER is the default
		// client.getHttpsSettings().setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

		// Define the supported user Token policies
		server.addUserTokenPolicy(UserTokenPolicy.ANONYMOUS);
		server.addUserTokenPolicy(UserTokenPolicy.SECURE_USERNAME_PASSWORD);
		server.addUserTokenPolicy(UserTokenPolicy.SECURE_CERTIFICATE);
		// Define a validator for checking the user accounts
		server.setUserValidator(userValidator);

		// Register on the local discovery server (if present)
		try {
			UaAddress discoveryAddress = new UaAddress(discoveryServerUrl);
			server.setDiscoveryServerAddress(discoveryAddress);
		} catch (URISyntaxException e) {
			logger.error("DiscoveryURL is not valid", e);
		}

		// server.setDiscoveryEndpointEnabled(false);

		// *** init() creates the service handlers and the default endpoints
		// according to the above settings
		server.init();

		initBuildInfo();

		// "Safety limits" for ill-behaving clients
		server.getSessionManager().setMaxSessionCount(500);
		server.getSessionManager().setMaxSessionTimeout(3600000); // one hour
		server.getSubscriptionManager().setMaxSubscriptionCount(500);

		// You can do your own additions to server initializations here

	}

	/**
	 * Load information models into the address space. Also register classes, to
	 * be able to use the respective Java classes with
	 * NodeManagerUaNode.createInstance().
	 *
	 * See the codegen/Readme.md on instructions how to use your own models.
	 */
	protected void loadInformationModels() {
		// Uncomment to take the extra information models in use.

		// // Register generated classes
		// server.registerModel(com.prosysopc.ua.types.di.server.InformationModel.MODEL);
		// server.registerModel(com.prosysopc.ua.types.adi.server.InformationModel.MODEL);
		// server.registerModel(com.prosysopc.ua.types.plc.server.InformationModel.MODEL);
		//
		// // Load the standard information models
		// try {
		// server.getAddressSpace().loadModel(
		// UaServer.class.getResource("Opc.Ua.Di.NodeSet2.xml")
		// .toURI());
		// server.getAddressSpace().loadModel(
		// UaServer.class.getResource("Opc.Ua.Adi.NodeSet2.xml")
		// .toURI());
		// server.getAddressSpace().loadModel(
		// UaServer.class.getResource("Opc.Ua.Plc.NodeSet2.xml")
		// .toURI());
		// } catch (Exception e) {
		// throw new RuntimeException(e);
		// }
	}

	/*
	 * Main loop for user selecting OPC UA calls
	 */
	protected void mainMenu() {

		/******************************************************************************/
		/* Wait for user command to execute next action. */
		do {
			printMenu();

			try {
				Action action = readAction();
				if (action != null) {
					ActionResult actionResult = action.performAction(this);
					switch (actionResult) {
					case CLOSE_SERVER:
						return; // closes server
					case NOTHING:
						continue; // continue looping menu
					}
				}
			} catch (Exception e) {
				printException(e);
			}

		} while (true);
		/******************************************************************************/
	}

	/**
	 * Run the server.
	 *
	 * @param enableSessionDiagnostics
	 * @throws UaServerException
	 * @throws StatusException
	 */
	protected void run(boolean enableSessionDiagnostics)
			throws UaServerException, StatusException {
		server.start();
		initHistory();
		if (enableSessionDiagnostics)
			server.getNodeManagerRoot().getServerData()
					.getServerDiagnosticsNode().setEnabled(true);

		//start simulation
		thread.start();
		
		// *** Main Menu Loop
		mainMenu();

		// Notify the clients about a shutdown, with a 5 second delay
		println("Shutting down...");
		server.shutdown(5, new LocalizedText("Closed by user", Locale.ENGLISH));
		println("Closed.");
	}
	
	//Simulation thread
	Thread thread = new Thread(){
	    public void run(){
	    	System.out.println("Thread Running");
	    	
	    	UaVariable testNode = null;
			try {
				testNode = (UaVariable)(server.getAddressSpace().getNode(new NodeId(2, "TESTNODE")));
			} catch (StatusException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
	    	while(true) {
	    		try {
					sleep(5000);
					if(testNode != null) {
						testNode.setValue(testNode.getValue().getValue().doubleValue() + 1);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (StatusException e) {
					e.printStackTrace();
				}
	    	}
	    }
	};

}
