/*
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

    Copyright (C) 2011-2013 Timo Rantalainen
*/

package deakin.timo.capture;

import deakin.timo.xBimuSample.RealTimeVisualizeAxes; /*Import ui*/
import jssc.SerialPortException;
import java.util.ArrayList;
import java.util.Arrays;
import deakin.timo.visualizeAxes.*;			/*Visualize axes jogl*/
import deakin.timo.visualizeAxes.utils.*;	/*Quaternion*/
import deakin.timo.madgwickAHRS.*;			/*Madgwick AHRS*/

public class CaptureXBIMU extends Capture{
	int sensorByteLength = 21;	/**sensor buffer length*/
	int batteryByteLength = 5;	/**battery byte length, to be ignored for now*/
	int maxByteLength = 21;	/**Max buffer length*/
	int minByteLength = 5;	/**Min buffer length*/
	byte[] dataInBuffer = null;		/**Read data into temp buffer*/
	int bufferPointer = 0;
	short[] valuesIn;
	short[] batteryIn;
	int channelsToVisualize = 6;
	VisualizeAxes visualizeWindow;
	private Quaternion quat = null;
	double[] Z = {0d,0d,1d}; 
	Quaternion zAxis = null; 
	MadgwickAHRS madgwickAHRS = null;
	
	/*Constructor*/
	public CaptureXBIMU(RealTimeVisualizeAxes mainProgram,VisualizeAxes visualizeWindow){
		super(mainProgram);
		this.visualizeWindow = visualizeWindow;
		madgwickAHRS = new MadgwickAHRSIMU(0.1d, new double[]{1,0,0,0}, 256d);
		noChannels = 9;
		setChannels(noChannels);		
		valuesIn = new short[noChannels];
		batteryIn = new short[1];
		zAxis = new Quaternion(0,Z[0],Z[1],Z[2]);
		System.out.println("Start reserving memory for dataRot");
		/*Add array for rotated data*/
		dataRot = new  ArrayList<ArrayList<Integer>>(3);
		for (int i = 0; i<3;++i){
			dataRot.add(new ArrayList<Integer>(historyLength));
			for (int j = 0;j<historyLength;++j){
				dataRot.get(i).add(0);
			}
		}
		System.out.println("Reserved memory for dataRot "+dataRot.get(0).size());
	}
	
	@Override
	public void run() {
		try{
			while (mainProgram.continueSampling) {
				/*Read at least maxByteLength bytes from the serial port*/
				int bytesReady = mainProgram.serialPort.getInputBufferBytesCount();
				while (mainProgram.continueSampling && bytesReady < maxByteLength){
					try{
						Thread.sleep(1);	/*Sleep for 1 ms...*/
						bytesReady = mainProgram.serialPort.getInputBufferBytesCount();
					} catch (Exception err){
						System.out.println("Sleeping and checking buffer byte count failed");
						break;
					}
				}
				byte[] tempBuffer = mainProgram.serialPort.readBytes(bytesReady);

				if (dataInBuffer == null){
					//System.out.println("dataInBuffer null");
					dataInBuffer = copyByteArray(tempBuffer);
				}else{
					//System.out.println("Existing data");
					byte[] tb = copyByteArray(dataInBuffer);
					dataInBuffer = new byte[tb.length+tempBuffer.length];
					for (int i = 0;i<tb.length;++i){
						dataInBuffer[i] = tb[i];
					}
					for (int i = 0;i<tempBuffer.length;++i){
						dataInBuffer[tb.length+i] = tempBuffer[i];
					}
					//System.out.println("Appended "+Integer.toString(dataInBuffer.length));
				}
				
				//System.out.println("decoding "+Integer.toString(dataInBuffer.length));

				/*Handle the byte buffer read*/
				bufferPointer = 0;
				//System.out.println("Start going through the buffer");
				if (dataInBuffer != null){
					Boolean goOn = true;
					while (goOn && dataInBuffer.length-bufferPointer > minByteLength){
						
						/**Search for the first byte with correct char*/
						while (bufferPointer < (dataInBuffer.length-minByteLength) && dataInBuffer[bufferPointer] != 'S' && dataInBuffer[bufferPointer] != 'B'){
							++bufferPointer;
						}
						//System.out.println("Going to switch");
						/**Select how to handle data*/
						if (bufferPointer < (dataInBuffer.length-minByteLength)){
							switch (dataInBuffer[bufferPointer]){
								case 'B':
									//System.out.println("B chosen");
									if(bufferPointer < (dataInBuffer.length-batteryByteLength)){
										//System.out.println("Case B");
										byte[] batteryPacket = new byte[batteryByteLength];
										int tempInd = 0;
										for (int i = bufferPointer;i<bufferPointer+batteryByteLength;++i){
											batteryPacket[tempInd] = dataInBuffer[i];
											++tempInd;
										}
										if (checkByteSumValue(batteryPacket) == 0){
											decodeBatteryPacket(batteryPacket);	/**Decode battery packet*/
											bufferPointer+=batteryByteLength;	/*Set the pointer to the next packet*/
											//System.out.println("BatteryPacket "+Byte.toString(batteryPacket[batteryPacket.length-2]));
										}else{
											++bufferPointer;	/**False alarm*/
											System.out.println("BatteryPacket checksumFail"+Byte.toString(checkByteSumValue(batteryPacket)));
										}
									
									}else{
										goOn = false;
										//System.out.println("B chosen too little data "+Integer.toString(dataInBuffer.length-bufferPointer));
									}
									break;
								case 'S':
									
									if(bufferPointer < (dataInBuffer.length-sensorByteLength)){
										//System.out.println("S chosen "+Integer.toString(dataInBuffer.length-bufferPointer));
										byte[] sensorPacket = new byte[sensorByteLength];
										int tempInd = 0;
										for (int i = bufferPointer;i<bufferPointer+sensorByteLength;++i){
											sensorPacket[tempInd] = dataInBuffer[i];
											++tempInd;
										}
										if (checkByteSumValue(sensorPacket) == 0){	
											decodeSensorPacket(sensorPacket);	/**Decode sensor packet*/
											bufferPointer+=sensorByteLength;	/*Set the pointer to the next packet*/
										}else{
											++bufferPointer;	/**False alarm*/
											System.out.println("SensorPacket checksumFail "+Byte.toString(checkByteSumValue(sensorPacket)));
										}
									}else{
										goOn = false;
										//System.out.println("S chosen too little data "+Integer.toString(dataInBuffer.length-bufferPointer));
									}
									
									break;
							}
						}else{
							goOn = false;
						}
					}
					//System.out.println("Save remaining bytes "+Integer.toString(dataInBuffer.length-bufferPointer));
					if (bufferPointer < (dataInBuffer.length)){
						byte[] tempBuffer2 = new byte[dataInBuffer.length-bufferPointer];
						int inde = 0;
						for (int i = bufferPointer;i<dataInBuffer.length;++i){
							tempBuffer2[inde] = dataInBuffer[i];
							++inde;
						}
						dataInBuffer = tempBuffer2;
					}else{
						dataInBuffer = null;
					}
					
				}else{
					System.out.println("Read null");
				}
				
			}
			/*All done*/
			doneSampling();
		}catch (SerialPortException ex){
			System.out.println("Capture run error");
			System.out.println(ex);
		}	
	}
	
	private byte[] copyByteArray(byte[] arrayIn){
		byte[] copyOfArrayIn = new byte[arrayIn.length];
		for (int i = 0; i<arrayIn.length;++i){
			copyOfArrayIn[i] = arrayIn[i];
		}
		//System.out.println("Copied an array "+Integer.toString(copyOfArrayIn.length));
		return copyOfArrayIn;
	}
	
	/**Decode battery packet*/
	private void decodeBatteryPacket(byte[] bufferIn){
		//Battery mV
		batteryIn[0] =(short)((((short) (bufferIn[1] & 0xff))<< 8) | (bufferIn[2] & 0xff));
		byte currentCount = bufferIn[3];
		//update voltage window
		//mainProgram.batteryWindow.plotNumber(((float) batteryIn[0])/1000f);
	}
	
	/**Decode sensor packet*/
	private void  decodeSensorPacket(byte[] bufferIn){
		/*Check sampling machine time*/
		currentNanos = System.nanoTime();	
		for (int i = 0;i<noChannels;++i){
			valuesIn[i] =(short)((((short) (bufferIn[2*i+1] & 0xff))<< 8) | (bufferIn[2*i+2] & 0xff));
		}
		byte currentCount = bufferIn[2*noChannels+1];
		
		
		/*Calculate and set quaternion!!*/
		/*Scale the values*/
		double[] imuData = new double[6];
		imuData[0] = ((double)valuesIn[3])/1000d;
		imuData[1] = ((double)valuesIn[4])/1000d;
		imuData[2] = ((double)valuesIn[5])/1000d;
		imuData[3] = ((double)valuesIn[0])/(10d*180d)*Math.PI;
		imuData[4] = ((double)valuesIn[1])/(10d*180d)*Math.PI;
		imuData[5] = ((double)valuesIn[2])/(10d*180d)*Math.PI;
		
		double initTheta;
		double[] rotAxis;
		/*The initial round*/
		if (quat == null){
			//Set the initial orientation according to first sample of accelerometry
			System.out.println("X "+Double.toString(imuData[0]) +" Y "+Double.toString(imuData[1])+" Z "+Double.toString(imuData[2]));
			initTheta =Math.acos(dot(normalize(new double[]{imuData[0],imuData[1],imuData[2]}),Z));
			rotAxis = cross(new double[]{imuData[0],imuData[1],imuData[2]},Z);
			//System.out.println("X "+Double.toString(rotAxis[0]) +" Y "+Double.toString(rotAxis[1])+" Z "+Double.toString(rotAxis[2])+" norm "+Double.toString(norm(rotAxis))+" cos "+Double.toString(Math.cos(initTheta/2d))+" "+Double.toString(initTheta));
			if (norm(rotAxis) != 0){
				rotAxis = normalize(rotAxis);
				//quat = new Quaternion(Math.cos(initTheta/2d),-Math.sin(initTheta/2d)*rotAxis[0],-Math.sin(initTheta/2d)*rotAxis[1],-Math.sin(initTheta/2d)*rotAxis[2]);
				quat = new Quaternion(Math.cos(initTheta/2d),Math.sin(initTheta/2d)*rotAxis[0],Math.sin(initTheta/2d)*rotAxis[1],Math.sin(initTheta/2d)*rotAxis[2]);
			}else{
				quat = new Quaternion(1d,0d,0d,0d);
			}
			madgwickAHRS.setOrientationQuaternion(quat.getDouble());
			//System.out.println(Double.toString(initTheta) +" "+Double.toString(Math.cos(initTheta/2d))+" "+Double.toString(-Math.sin(initTheta/2d)*rotAxis[0])+" "+Double.toString(-Math.sin(initTheta/2d)*rotAxis[1])+" "+Double.toString(-Math.sin(initTheta/2d)*rotAxis[2]) );
			System.out.println(quat.toString());
		}else{
			/*Use Madgwick AHRS IMU algorithm*/
			madgwickAHRS.AHRSUpdate(new double[] {imuData[3],imuData[4],imuData[5],imuData[0],imuData[1],imuData[2]});
			double[] tempQ = madgwickAHRS.getOrientationQuaternion();
			quat = new Quaternion(tempQ[0],tempQ[1],tempQ[2],tempQ[3]);
		}
		
		if (quat != null){
			visualizeWindow.setRotationQuaternion(quat.getFloat());
			//Calculated rotated values
			
			Quaternion grf = new Quaternion(0d,imuData[0],imuData[1],imuData[2]);
			//Quaternion rotatedQ = ((quat.conjugate()).times(grf)).times(quat);
			Quaternion rotatedQ = (quat.times(grf)).times(quat.conjugate());
			double[] rotatedVals = rotatedQ.getAxis();
			//System.out.println("Got to rotating data X "+rotatedVals[0]+" Y "+rotatedVals[1]+" Z "+rotatedVals[2]);
			/*Assign the values to ArrayLists*/
			for (int i = 0; i < 3;++i){
				if (capturedDataPoints < historyLength){
					dataRot.get(i).set(capturedDataPoints,(int) rotatedVals[i]); 
				}else{
					dataRot.get(i).add((int) (rotatedVals[i]*1000.0d+500.0));
					dataRot.get(i).remove(0);	//Remove the oldest value
				}
			}
		}
		
		/*Assign the values to ArrayLists*/
		for (int i = 0; i < noChannels;++i){
			if (capturedDataPoints < historyLength){
				data.get(i).set(capturedDataPoints,(int) valuesIn[i]); 
			}else{
				data.get(i).add((int) valuesIn[i]);
				data.get(i).remove(0);	//Remove the oldest value
			}
		}
		
		if (capturedDataPoints < historyLength){
			++capturedDataPoints;
		}
		
		/*print results to file*/
		try{
			/*Change the int[] to byte[] for writing*/
			byte[] valueBytes = new byte[(valuesIn.length+1)*2];	//Include battery voltage
			for (int i = 0; i<valuesIn.length;++i){
				for (int j = 0; j<2;++j){
					valueBytes[i*2+j] =(byte) (0xff & (valuesIn[i]>>(8*j)));	//Change from java Big endian to little endian
				}
			}
			//Battery voltage
			for (int j = 0; j<2;++j){
				valueBytes[valuesIn.length*2+j] =(byte) (0xff & (batteryIn[0]>>(8*j)));	//Change from java Big endian to little endian
			}
			//System.out.println("BAttery "+Short.toString(batteryIn[0]));
			//Write the channel results
			mainProgram.oStream.write(valueBytes);
			/**write sampling nanos, score, timerStarted, dualTaskEnabled*/
			byte[] extra = new byte[8+1+1+1+2]; //long,  byte, byte,byte,short
			extra = longToBytes(currentNanos,extra,0);
			extra[8] = mainProgram.timerStarted ? (byte) 1:(byte) 0;
			extra[9] =  mainProgram.dualTaskEnabled ? (byte) 1:(byte) 0;
			extra[10] = currentCount;
			//current task
			for (int j = 0; j<2;++j){
				extra[11+j] =(byte) (0xff & (mainProgram.currentTask>>(8*j)));	//Change from java Big endian to little endian
			}
			//Write the extra id info
			mainProgram.oStream.write(extra);
			
		} catch (Exception err){
			//System.out.println("Couldn't write sensor data");
		}
		
		
		++plotCount;
		if (plotCount >= plotEveryNth){
			plotCount -= plotEveryNth;
			//mainProgram.textLabel.setText(Long.toString(timeStamp));
			mainProgram.drawImage.clearPlot();
			mainProgram.drawImage2.clearPlot();
			mainProgram.rotatedWindow.clearPlot();
			/*Plot the traces*/
			for (int i = 0; i < 3;++i){
				mainProgram.drawImage.plotTrace(data.get(i).toArray(new Integer[]{}),i);
			}
			for (int i = 3; i < 6;++i){
				mainProgram.drawImage2.plotTrace(data.get(i).toArray(new Integer[]{}),i-3);
			}
			/*Plot rotated data*/
			for (int i = 0; i < 3;++i){
				mainProgram.rotatedWindow.plotTrace(dataRot.get(i).toArray(new Integer[]{}),i);
			}
			
			
			mainProgram.drawImage.paintImageToDraw();
			mainProgram.drawImage2.paintImageToDraw();
			mainProgram.rotatedWindow.paintImageToDraw();
		}
	}

	@Override
	protected void doneSampling(){
		//mainProgram.tare.setEnabled(false);
		/*Close the save file*/
		try{
			if (mainProgram.oStream != null){
				mainProgram.oStream.flush();
				mainProgram.oStream.close();
				mainProgram.oStream = null;
			}
		}catch (Exception ex){
			System.out.println(ex);
		}
		/*Disable and enable buttons*/
		mainProgram.endSampling.setEnabled(false);
		mainProgram.beginSampling.setEnabled(true);
		mainProgram.chooseSaveFile.setEnabled(true);
	}
	
	/*
	CheckSum from x-io
	        private byte CalcChecksum(byte packetLength)
        {
            byte tempRxBufIndex = (byte)(binBufIndex - packetLength);
            byte checksum = 0;
            while (tempRxBufIndex != binBufIndex)
            {
                checksum ^= binBuf[tempRxBufIndex++];
            }
            return checksum;
        }
	
	*/
	
	public byte checkByteSumValue(byte[] bufferIn){
		byte checksum = 0;
		byte tempRxBufIndex = 0;
		while (tempRxBufIndex < bufferIn.length){
			checksum ^= bufferIn[tempRxBufIndex];
			++tempRxBufIndex;
        }
		return checksum;
	}
	
	
	/**Quaternion orientation stuff*/
	/*
	private float[] normalize(float[] a){
		float magnitude = norm(a);
		for (int i = 0;i<a.length;++i){
			a[i]= a[i]/magnitude;
		}
		return a;
	}
	*/
	
	private double[] normalize(double[] a){
		double magnitude = norm(a);
		for (int i = 0;i<a.length;++i){
			a[i]= (a[i]/magnitude);
		}
		return a;
	}
	
	/*
	private float[] diff(float[] arrIn){
		float[] arrOut = new float[arrIn.length-1];
		for (int i = 0;i<arrIn.length-1;++i){
			arrOut[i] = arrIn[i+1]-arrIn[i];
		}
		return arrOut;
	}
	*/
	
	private double[] diff(double[] arrIn){
		double[] arrOut = new double[arrIn.length-1];
		for (int i = 0;i<arrIn.length-1;++i){
			arrOut[i] = arrIn[i+1]-arrIn[i];
		}
		return arrOut;
	}
	
	/*
	private float mean(float[] a){
		float b = 0;
		for (int i = 0;i<a.length;++i){
			b += a[i]/((float)a.length);
		}
		return b;
	}
	*/
	
	private double mean(double[] a){
		double b = 0;
		for (int i = 0;i<a.length;++i){
			b += a[i]/((double)a.length);
		}
		return b;
	}
	
	/*
	private float dot(float[] a, float[] b){
		return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
	}
	*/
	
	private double dot(double[] a, double[] b){
		return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
	}
	
	/*
	private float[] cross(float[] a, float[] b){
		float[] c =new float[3];
		c[0] = a[1]*b[2]-a[2]*b[1];
		c[1] = a[2]*b[0]-a[0]*b[2];
		c[2] = a[0]*b[1]-a[1]*b[0];
		return c;
	}
	*/
	private double[] cross(double[] a, double[] b){
		double[] c =new double[3];
		c[0] = (a[1]*b[2]-a[2]*b[1]);
		c[1] = (a[2]*b[0]-a[0]*b[2]);
		c[2] = (a[0]*b[1]-a[1]*b[0]);
		return c;
	}

	/*
	private float norm(float[] a){
		float b=0;
		for (int i = 0;i<a.length;++i){
			b+=a[i]*a[i];
		}	
		return (float)(Math.sqrt(b));
	}
	*/
	
	private double norm(double[] a){
		double b=0;
		for (int i = 0;i<a.length;++i){
			b+=a[i]*a[i];
		}	
		return Math.sqrt(b);
	}
	
	/*
	private float norm(float a, float b, float c){
		float d=a*a+b*b+c*c;
		return (float)(Math.sqrt(d));
	}
	*/
	private double norm(double a, double b, double c){
		return Math.sqrt(a*a+b*b+c*c);
	}
	
	
	
}

