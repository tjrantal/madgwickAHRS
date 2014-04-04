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

public class Capture implements Runnable{
	public ArrayList<ArrayList<Integer>> data;
	public ArrayList<ArrayList<Integer>> dataRot;
	public double score = 0;
	protected int capturedDataPoints;
	protected int historyLength = 1000;
	protected int plotEveryNth = 10;//100;
	protected int plotCount = 0;
	protected int noChannels = 4;
	protected int[] zeros;
	protected int bufferIndex;
	protected RealTimeVisualizeAxes mainProgram;
	protected long currentNanos;
	/*Constructor*/
	public Capture(RealTimeVisualizeAxes mainProgram){
		this.mainProgram = mainProgram;
		setChannels(noChannels);
	}
	
	protected void setChannels(int noChannels){
		data = new  ArrayList<ArrayList<Integer>>(noChannels);
		zeros = new int[noChannels];
		for (int i = 0; i<noChannels;++i){
			zeros[i] = (int) (Math.pow(2.0,10.0)-1);
		}
		for (int i = 0; i<noChannels;++i){
			data.add(new ArrayList<Integer>(historyLength));
			for (int j = 0;j<historyLength;++j){
				data.get(i).add(0);
			}
		}
		capturedDataPoints = 0;
		plotCount = 0;
	}
	
	public void resetScore(){
		score = 0;
	}
	public void setZeros(){
		for (int i = 0; i<zeros.length;++i){
			try {
				zeros[i] = getMean(data.get(i));
				System.out.print(zeros[i]+"\t");
			} catch (Exception err){}
		}		
		System.out.println();
	}
	
	protected int getMean(ArrayList<Integer> arr) throws Exception {
		if (arr.isEmpty()){
			throw new Exception();
		}else{
			int size = arr.size();
			int currPoint = min(size,capturedDataPoints-1);
			int meanSize = min(20,currPoint);
			int meanVal = 0;
			for (int i = (currPoint-meanSize); i<currPoint;++i){
				meanVal+=arr.get(i);
			}
			meanVal = (int)(((double)meanVal)/((double)meanSize));
			return meanVal;
		}
	}
	
	protected int min(int a, int b){
		if (a <= b) {
			return a;
		}else{
			return b;
		}		
	}
	
	public void run() {
		try{
			byte[] bufferIn;
			bufferIn = new byte[10];	/*Arduino is sending 9 bytes at a time for sampling 3 A/D channels*/
			int[] valuesIn = new int[noChannels];
			byte[] tempByteBuffer;
			tempByteBuffer = new byte[1];
			int positionInBuffer = 0;
			int checkSum;
			int checkSumReceived;
			long timeStamp = 0,prevtStamp = 0;
			double dt = 0;
			
			while (mainProgram.continueSampling) {
				mainProgram.serialPort.writeByte((byte)1);	/*Write 1 to let the arduino know that we're good to go*/
				//System.out.println("Start sampling");
				while (mainProgram.continueSampling && mainProgram.serialPort.getInputBufferBytesCount() <bufferIn.length){
					try{
						Thread.sleep(0,100000);	/*Sleep for 1 ms...*/
					} catch (Exception err){
						System.out.println("receiving failed "+bufferIn.length);
						break;
					}
				}
				if (mainProgram.serialPort.getInputBufferBytesCount() >= bufferIn.length){
					bufferIn = mainProgram.serialPort.readBytes(bufferIn.length);
				}else{
					doneSampling();	/*Something has gone wrong, stop sampling*/
					return;
				}
				
				if (bufferIn != null){
					/*Check that we've got a full packet */
					checkSumReceived = bufferIn[bufferIn.length-1]  & 0x0f;
					checkSum  = checkSumValue(bufferIn);	
					
					/*read more a byte at a time until checksum matches*/
					while (checkSum != checkSumReceived) {	
					
					
						//the checksum does not match - move bytes towards the beginning of the array by one
						for (int i=1; i<bufferIn.length; ++i){
						  bufferIn[i-1] = bufferIn[i];
						}
						/*Check that at least one byte of data is available*/
						while (mainProgram.serialPort.getInputBufferBytesCount() <1){
							try{
								Thread.sleep(1);	/*Sleep for 10 ms...*/
							} catch (Exception err){break;}
						}						
						tempByteBuffer = mainProgram.serialPort.readBytes(1); //Read one more byte of data
						bufferIn[bufferIn.length-1] = tempByteBuffer[0];	//assign the latest byte to the end of the array
						checkSumReceived = bufferIn[bufferIn.length-1]  & 0x0f;
						checkSum  = checkSumValue(bufferIn);	
					}    
					/*If we get through check sum, we've got a full packet*/
					/*Check sampling machine time*/
					currentNanos = System.nanoTime();					
					/*Extract sampled values and time stamp*/
					/*Get number of channels sent*/
					int receivedChans = (bufferIn[bufferIn.length-1] >> 4) & 0x0f;
					bufferIndex = 0;
					for (int currentValue = 0; currentValue < noChannels;++currentValue){
						/*Extract the 10 bit values from the byte buffer*/
						if (currentValue%4 == 0){
							valuesIn[currentValue] = ((0xff & bufferIn[bufferIndex])<<2) | ((bufferIn[bufferIndex+1] & 0xff)>>6);
							++bufferIndex;
						}
						if (currentValue%4 == 1){
							valuesIn[currentValue] = ((0x3f & bufferIn[bufferIndex])<<4) | ((bufferIn[bufferIndex+1] & 0xff)>>4);
							++bufferIndex;
						}
						if (currentValue%4 == 2){
							valuesIn[currentValue] = ((0x0f & bufferIn[bufferIndex])<<6) | ((bufferIn[bufferIndex+1] & 0xff)>>2);
							++bufferIndex;
						}
						if (currentValue%4 == 3){
							valuesIn[currentValue] = ((0x03 & bufferIn[bufferIndex])<<8) | ((bufferIn[bufferIndex+1] & 0xff));
							bufferIndex+=2;
						}
					}
					/*Assign the values to ArrayLists*/
					for (int i = 0; i < noChannels;++i){
						if (capturedDataPoints < historyLength){
							data.get(i).set(capturedDataPoints,valuesIn[i]); 
						}else{
							data.get(i).add(valuesIn[i]);
							data.get(i).remove(0);	//Remove the oldest value
						}
					}
					
					if (capturedDataPoints < historyLength){
						++capturedDataPoints;
					}
					
					
					
					
					/*Timestamp*/
					int bytesForVals = (int) (Math.ceil(((double) receivedChans)*5.0/4.0));
					timeStamp = 0;
					for (int i = 0;i<4;++i){
					  timeStamp = timeStamp | ((bufferIn[bytesForVals+i] & 0xff)<< (8*(3-i))) ; 
					}
					
					dt = ((double)(timeStamp-prevtStamp))/1000000.0;
					prevtStamp = timeStamp;
					/*Integrate result*/
					if(mainProgram.timerStarted){
						/*Figure where the ball is*/
						/*The channel with the largest difference is the ball. If two channels have roughly the same difference, use their mean*/
						int[] diff = new int[valuesIn.length];
						int maxDiff = Integer.MIN_VALUE;
						int channel = -1;
						for (int i = 0;i<valuesIn.length;++i){
							diff[i] = zeros[i]-valuesIn[i]; /*Looking for largest positive value*/
							if (diff[i] > maxDiff){
								maxDiff = diff[i];
								channel = i;
							}							
						}
						score += ((double)channel)*dt;					
					}
					
					
					/*print results to file*/
					try{
						/*Write the time stamp first*/
						byte[] tStampBytes = new byte[4];
						
						for (int j = 0; j<4;++j){
								tStampBytes[j] = (byte) (0xff & (timeStamp>>(8*j)));	//Change from java Big endian to little endian
							}
						mainProgram.oStream.write(tStampBytes);	/*Write straight from the buffer*/
						/*Change the int[] to byte[] for writing*/
						byte[] valueBytes = new byte[valuesIn.length*4];
						for (int i = 0; i<valuesIn.length;++i){
							for (int j = 0; j<4;++j){
								valueBytes[i*4+j] =(byte) (0xff & (valuesIn[i]>>(8*j)));	//Change from java Big endian to little endian
							}
						}
						//Write the channel results
						mainProgram.oStream.write(valueBytes);
						/**write sampling nanos, score, timerStarted, dualTaskEnabled*/
						byte[] extra = new byte[8+8+1+1]; //long, double, byte, byte
						extra = longToBytes(currentNanos,extra,0);
						long longScore = Double.doubleToLongBits(score);
						extra = longToBytes(longScore,extra,8);
						extra[16] = mainProgram.timerStarted ? (byte) 1:(byte) 0;
						extra[17] =  mainProgram.dualTaskEnabled ? (byte) 1:(byte) 0;
						//Write the extra id info
						mainProgram.oStream.write(extra);
						/**Write the zeros*/
						byte[] zeroBytes = new byte[noChannels*4];
						for (int i = 0;i<noChannels;++i){
							for (int j = 0; j<4;++j){
									zeroBytes[i*4+j] = (byte) (0xff & (zeros[i]>>(8*j)));	//Change from java Big endian to little endian
								}
						}
						mainProgram.oStream.write(zeroBytes);	/*Write straight from the buffer*/
						
					} catch (Exception err){
						//System.out.println("Couldn't write sensor data");
					}
					
					
					++plotCount;
					if (plotCount >= plotEveryNth){
						plotCount -= plotEveryNth;
						//mainProgram.textLabel.setText(Long.toString(timeStamp));
						mainProgram.drawImage.clearPlot();
						/*Plot the traces*/
						for (int i = 0; i < noChannels;++i){
							mainProgram.drawImage.plotTrace(data.get(i).toArray(new Integer[]{}),i,zeros[i]);
						}
						mainProgram.drawImage.paintImageToDraw();
						//mainProgram.resultWindow.plotNumber((float) score);
						//System.out.print("Last value "+value+" timeStamp "+timeStamp+"\r");
					}
				}else{
					System.out.print("Didn't get data\r");
				}
			}
			/*All done*/
			doneSampling();
		}catch (SerialPortException ex){
			System.out.println(ex);
		}	
	}
	
	protected byte[] longToBytes(long val,byte[] buffer, int offset){
		for (int j = 0; j<8;++j){
			buffer[offset+j] =(byte) (0xff & (val>>(8*j)));	//Change from java Big endian to little endian
		}
		return buffer;
	}
	
	protected void doneSampling(){
		//mainProgram.tare.setEnabled(false);
		try{
			mainProgram.serialPort.writeByte((byte)2);	/*Write 2 to let the arduino know that we're done*/
		}catch (SerialPortException ex){
			System.out.println(ex);
		}
		/*Close the save file*/
		try{
			mainProgram.oStream.flush();
			mainProgram.oStream.close();
			mainProgram.oStream = null;
		}catch (Exception ex){
			System.out.println(ex);
		}
		/*Disable and enable buttons*/
		mainProgram.endSampling.setEnabled(false);
		mainProgram.beginSampling.setEnabled(true);
		mainProgram.chooseSaveFile.setEnabled(true);
	}
	
	public int checkSumValue(byte[] bufferIn){
		int checkSum = 0;
		for (int i = 0; i<bufferIn.length-1;++i){
			checkSum += ((bufferIn[i] >> 4) & 0x0f)+(bufferIn[i] & 0x0f);
		}
		checkSum += ((bufferIn[bufferIn.length-1] >> 4) & 0x0f);
		while (checkSum > 0x0f) checkSum = (checkSum >> 4)+(checkSum & 0x0f); 
		return checkSum;	
	}
}

