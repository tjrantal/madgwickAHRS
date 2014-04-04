/*
	Use simple serial connector library to access bluetooth serial port http://code.google.com/p/java-simple-serial-connector/
	Use javax.swing for UI creation
*/
package deakin.timo.xBimuSample;
/*Swing classes*/
import javax.swing.*;		//GUI commands swing
import java.awt.event.*; 	//Events & Actionlistener
import java.io.*;				//File IO
import java.lang.Math;
import java.awt.*;
import java.awt.geom.Line2D;
import javax.swing.event.*;
import javax.swing.border.*;
/*Simple serial connector classes*/
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
/*Capture thread*/
import deakin.timo.capture.*;
/*DrawImage*/
import deakin.timo.DrawImage.*;
/*Choosing and saving a file*/
import javax.swing.SwingUtilities;
import javax.swing.filechooser.*;
import java.util.prefs.Preferences;		/*Saving the file save path -> no need to rebrowse...*/

/*Timestamp*/
import java.sql.Timestamp;
import java.util.Date;

//import javax.media.opengl.awt.GLJPanel;	/*jogl panel*/
import javax.media.opengl.*;			/*jogl Capabilities*/
import deakin.timo.visualizeAxes.*;			/*Visualize axes jogl*/

/*
	ActionListener for actions with Swing buttons etc.
	KeyListener to get key events for starting and stopping sampling
	WindowListener to close the save file gracefully at the end.

*/

public class RealTimeVisualizeAxes extends JPanel implements ActionListener, WindowListener{
	private JComboBox comPortDropDownMenu;
	public JButton chooseSaveFile;			/*For saving the results to a file*/
	private JFileChooser fileChooser;		/*Selecting the file to save to*/
	private Preferences preferences;		/**Saving the default file path*/
	private final String keySP = "SP";
	private String savePath;
	private JButton connectBluetooth;
	//public JButton tare;
	public JButton beginSampling;
	public JButton endSampling;
	public JButton singleTask;
	public JButton dualTask;
	public Boolean dualTaskEnabled = false;	/**Used to enable dual task timer*/
	public Boolean timerStarted = false; /**Used to indicate whether dual task is in session*/
	private JButton closeBluetooth;
	
	private JComboBox taskDropDown;
	public short currentTask = 0;
	
	private String portToConnectTo;
	public DrawImage drawImage;
	public DrawImage drawImage2;
	public Boolean continueSampling;
	public SerialPort serialPort;
	public JLabel textLabel;
	private static int imWidth = 800;
	private static int imHeight = 256;
	public BufferedOutputStream oStream = null; 
	/**For results; timing & windows*/

	public DrawImage batteryWindow;
	public DrawImage rotatedWindow;
	public VisualizeAxes visualizeWindow;
	public long startTime;
	
	public Boolean integratorOn = false;
	private Capture capture = null;
	private Thread captureThread = null;
	//private Date date;
	private File file = null;	/*Used to get a file name for fileoutputstream*/
	
	public RealTimeVisualizeAxes(JFrame parentFrame){
		portToConnectTo = null;
		serialPort = null;
		JPanel buttons = new JPanel(); /*Panel for start and stop*/
		//JPanel buttons2 = new JPanel(); /*Panel for start and stop*/
		/**Add save file chooser*/
		chooseSaveFile = new JButton("Select save file");
		chooseSaveFile.setMnemonic(KeyEvent.VK_S);
		chooseSaveFile.setActionCommand("chooseSaveFile");
		chooseSaveFile.addActionListener(this);
		chooseSaveFile.setToolTipText("Press to select the file to save results to");
		chooseSaveFile.setEnabled(true);
		buttons.add(chooseSaveFile);	
		preferences = Preferences.userRoot().node(this.getClass().getName());
		try{
			savePath = preferences.get(keySP,new File( "." ).getCanonicalPath()); /*Use current working directory as
		default*/
		}catch (IOException ex){
			System.out.println(ex);
			savePath = ".";
		}
		/*Instantiate fileChooser*/
		fileChooser = new JFileChooser(savePath);							/*Implements the file chooser*/
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);	/*Limit to choosing files*/
		//date = new java.util.Date();	/*Date object to get a timestamp*/
		
		/*Add dropbox list of com ports*/
		comPortDropDownMenu = new JComboBox();
		String[] comPorts = SerialPortList.getPortNames();	//List com ports
		if (comPorts.length > 0){
			portToConnectTo = comPorts[0];
		}
		for(int i = 0; i < comPorts.length; ++i){
			comPortDropDownMenu.addItem(comPorts[i]);
		}
		comPortDropDownMenu.addActionListener(this);
		comPortDropDownMenu.setActionCommand("comPortDropDownMenu");
		buttons.add(comPortDropDownMenu);	
		
		/*Connect button*/
		connectBluetooth= new JButton("Connect Bluetooth");
		connectBluetooth.setMnemonic(KeyEvent.VK_B);
		connectBluetooth.setActionCommand("connectBluetooth");
		connectBluetooth.addActionListener(this);
		connectBluetooth.setToolTipText("Press to connect Bluetooth (selected serial port)");
		connectBluetooth.setEnabled(false);
		buttons.add(connectBluetooth);		
				
		/*Begin button*/
		beginSampling= new JButton("Begin sampling");
		beginSampling.setMnemonic(KeyEvent.VK_B);
		beginSampling.setActionCommand("beginSampling");
		beginSampling.addActionListener(this);
		beginSampling.setToolTipText("Press to begin sampling");
		beginSampling.setEnabled(false);
		buttons.add(beginSampling);
		
		/*End button*/
		endSampling= new JButton("End sampling");
		endSampling.setMnemonic(KeyEvent.VK_E);
		endSampling.setActionCommand("endSampling");
		endSampling.addActionListener(this);
		endSampling.setToolTipText("Press to end sampling");
		endSampling.setEnabled(false);
		buttons.add(endSampling);
		
		/*close bluetooth port button*/
		closeBluetooth= new JButton("Close Connection");
		closeBluetooth.setMnemonic(KeyEvent.VK_B);
		closeBluetooth.setActionCommand("closeBluetooth");
		closeBluetooth.addActionListener(this);
		closeBluetooth.setToolTipText("Press to close Bluetooth connection (selected serial port)");
		closeBluetooth.setEnabled(false);
		buttons.add(closeBluetooth);	
		
		/*Add the buttons panel to the GUI*/
		add(buttons);
		
		//textLabel = new JLabel("result");
		//add(textLabel);
		/*Figure for captured data*/
		drawImage = new DrawImage(new Dimension(imWidth/2,imHeight/2),(int) (Math.pow(2.0,16.0)));
		//drawImage.setBackground(new Color(0, 0, 0));
		//drawImage.setPreferredSize(new Dimension(imWidth,imHeight));
		drawImage.setOpaque(true);
		add(drawImage);
		drawImage.paintImageToDraw();
		
		drawImage2 = new DrawImage(new Dimension(imWidth/2,imHeight/2),(int) (Math.pow(2.0,12.0)));
		//drawImage.setBackground(new Color(0, 0, 0));
		//drawImage2.setPreferredSize(new Dimension(imWidth,imHeight));
		drawImage2.setOpaque(true);
		add(drawImage2);
		drawImage2.paintImageToDraw();
		
		visualizeWindow = new VisualizeAxes(imWidth/2,imHeight/2);
		visualizeWindow.setOpaque(true);
		//System.out.println("Calling reshape");
		//visualizeWindow.reshape(0,0,imWidth/2,imHeight/2);
		//System.out.println("Reshape called");
		add(visualizeWindow);
		
		/*
		batteryWindow = new DrawImage(new Dimension(imWidth/2,imHeight/2));
		batteryWindow.setOpaque(true);
		add(batteryWindow);
		batteryWindow.paintImageToDraw();
		*/
		
		rotatedWindow = new DrawImage(new Dimension(imWidth/2,imHeight/2),(int) (Math.pow(2.0,12.0)));
		rotatedWindow.setOpaque(true);
		add(rotatedWindow);
		rotatedWindow.paintImageToDraw();
		
		/**add WindowListener for closing graciously*/
		parentFrame.addWindowListener(this);
	}

	/**Implement ActionListener*/
	public void actionPerformed(ActionEvent e) {
		/**Select file for saving*/
		if ("chooseSaveFile".equals(e.getActionCommand())){
			int returnVal = fileChooser.showOpenDialog(this.getParent());
 
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();
				
				try{
					if (file.isDirectory()){
						preferences.put(keySP,file.getCanonicalPath());
					}else{
						preferences.put(keySP,(new File(file.getParent())).getCanonicalPath());
					}
					System.out.println("Save path set "+preferences.get(keySP,"."));
				}catch (IOException ex){
					System.out.println(ex);
					savePath = ".";
				}
            } else {
                System.out.println("Cancelled file dialog");
            }

		}
	
		/**Select com port*/
		if ("comPortDropDownMenu".equals(e.getActionCommand())){
			portToConnectTo = (String) ((JComboBox)e.getSource()).getSelectedItem();
			connectBluetooth.setEnabled(true);
		}
		
		
		/**Connect bluetooth*/
		if ("connectBluetooth".equals(e.getActionCommand())){
			connectBluetooth.setEnabled(false);
			
			/*Connect bluetooth here*/
			serialPort = new SerialPort(portToConnectTo);
			Boolean connectSuccess = false;
			int attempts = 0;
			while (attempts < 10 && !connectSuccess){
				try {
					System.out.println("Port opened: " + serialPort.openPort());
					System.out.println("Parameters set: " + serialPort.setParams(115200, 8, 1, 0));	/*Serial port has been set to 115200*/
					connectSuccess = true;
				}catch (SerialPortException ex){
					System.out.println(ex);
					++attempts;
					/**Sleep for 2 seconds prior to re-trying*/
					try{
						Thread.sleep(2000);
					}catch (Exception err){
						System.out.println("Could not sleep");
						System.out.println(err);
					}
				}	
			}
			//beginSampling.setEnabled(true);
			closeBluetooth.setEnabled(true);
			doBeginSampling();
			requestFocusInWindow();
			
		}
		

		
		/**Close connection*/
		
		if ("closeBluetooth".equals(e.getActionCommand())){
			closeBluetooth.setEnabled(false);
			if (serialPort != null) {
				try{
					serialPort.closePort();
					serialPort = null;
				} catch (Exception err){
					System.out.println("Couldn't close port");
				}
			}
				
		}
		
		/**Begin sampling*/
		if ("beginSampling".equals(e.getActionCommand())) {
			doBeginSampling();
		}
		

		if ("endSampling".equals(e.getActionCommand())){
			continueSampling = false;

			closeFile();
		}		
	}
	/**ActionListener done*/
	
	private void doBeginSampling(){
			endSampling.setEnabled(true);
			beginSampling.setEnabled(false);
			chooseSaveFile.setEnabled(false);
			continueSampling = true;
			/*Create a file for saving*/
			System.out.println("doBegin Close file");
			closeFile();
			System.out.println("doBegin Open file");
			openFile();			
			System.out.println("moving on");
			/*Start a thread for capturing*/
			System.out.println("Creating capture thread");
			capture = new CaptureXBIMU(this,visualizeWindow);
			captureThread = new Thread(capture,"captureThread");
			System.out.println("Thread created");
			captureThread.start();				
			System.out.println("Thread started");
			requestFocusInWindow();		
	}

	/**Method to initialize the GUI*/
	public static void initAndShowGUI(){
		JFrame f = new JFrame("Bluetooth (serial port) sampling");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		/*
		JComponent contentPane = new BluetoothTest();
		contentPane.setOpaque(true); //content panes must be opaque
		f.setContentPane(contentPane);
		*/
		f.add(new RealTimeVisualizeAxes(f));
		f.pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int w;
		int h;
		int traces = 1;
		if (screenSize.width < imWidth+40){w = screenSize.width-40;}else{w=imWidth+40;}
		if (screenSize.height < imHeight*traces+150){h = screenSize.height-40;}else{h=imHeight*traces+150;}
		f.setLocation(20, 20);
		//f.setLocation(screenSize.width/2 - w/2, screenSize.height/2 - h/2);
		f.setSize(w, h);
		System.out.println("Making window visible");
		f.setVisible(true);
		System.out.println("Window visible");
	}
	
	/**Open a new file for saving*/
	private void openFile(){
	
		long dateTime = (new java.util.Date()).getTime();
		System.out.println("Opening a new file "+Long.toString(dateTime));
		if (file != null){
			/**Append timestamp to make the file unique*/
			String fileToOpen = null;
			try{
				fileToOpen = file.getCanonicalPath();
				fileToOpen+="_xBIMU_";
				fileToOpen+=Long.toString(dateTime);//(date.getTime());
				System.out.println("Trying to open "+fileToOpen);
			}catch (Exception ex){
				System.out.println(ex);
			}
			try{
				/*Finalize any pre-existing outputstream*/
				if (oStream != null){
					oStream.flush();
					oStream.close();
					oStream = null;
				}


				oStream = new BufferedOutputStream(new FileOutputStream(fileToOpen));
				System.out.println("Opened "+file.getName()+" for writing");
			}catch (Exception err){
				System.out.println("Couldn't open the file");
			}
		}
	}
	
	/**Close the file currently saving to*/
	private void closeFile(){
		if (oStream != null){
			try{
				/*Finalize the outputstream*/
				oStream.flush();
				oStream.close();
				oStream = null;
			}catch (Exception err){
				System.out.println("Couldn't close the file");
			}
		}
	}
	
	/**Implement WindowListener*/
	@Override public void 	windowActivated(WindowEvent e){}
	/**Close down the bluetooth, and save the save file*/
	@Override public void 	windowClosed(WindowEvent e){}
	@Override public void 	windowClosing(WindowEvent e){


		continueSampling = false;
		/*Stop the capture thread*/
		if (captureThread != null){
			System.out.println("Waiting for capture to join");
			try{
				captureThread.join();
			} catch (Exception err){
				System.out.println("Couldn't join captureThread");
			}		
			System.out.println("Capture joined");
		}
		/*Close the save file if it is still open*/
		if (oStream != null){
			closeFile();
			System.out.println("Closed the save file");
		}
		/*Close the serial port*/
		if (serialPort != null){
			try{
				serialPort.closePort();
				serialPort = null;
			} catch (Exception err){
				System.out.println("Couldn't close port");
			}
		}
		System.out.println("All done");
	}
	@Override public void 	windowDeactivated(WindowEvent e){}
	@Override public void 	windowDeiconified(WindowEvent e){}
	@Override public void 	windowIconified(WindowEvent e){}
	
	/*Start the jogl window */
	@Override public void 	windowOpened(WindowEvent e){
		System.out.println("Window Opened");
		visualizeWindow.start();
		System.out.println("Started visualizeWindow");
	}
	
	/**MAIN, just call invokeLater to get the program started*/
	public static void main(String[] args){
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run(){
				initAndShowGUI();
			}
		}
		);
	}
	

}