/**
	Port of Seb Madqwick's open source IMU AHRS C implementation http://www.x-io.co.uk/open-source-imu-and-ahrs-algorithms/
	Written by Timo Rantalainen 2014 tjrantal at gmail dot com
	Licensed with GPL 3.0 (https://www.gnu.org/copyleft/gpl.html)
*/
package deakin.timo.madgwickAHRS;
public abstract class MadgwickAHRS{
	protected double beta;				/*< algorithm gain */
	protected double[] q;	/*< quaternion of sensor frame relative to auxiliary frame */
	protected double samplingFreq;
	/**
		Constructor
		@param beta algorithm gain
		@param q orientation quaternion
		@param samplingFreq Sampling frequency
	*/
	public MadgwickAHRS(double beta, double[] q, double samplingFreq){
		this.beta = beta;
		this.q = q;
		this.samplingFreq = samplingFreq;
	}

	/**
		Set the current orientation quaterion
		@param q Orientation quaterinion q, x , y, z.
	*/
	public void setOrientationQuaternion(double[] q){
		this.q = q;
	}
	
	
	/**
		Return the current orientation quaterion
	*/
	public double[] getOrientationQuaternion(){
		return q;
	}
	
	/**
		Update the orientation according to the latest set of measurements
		@param AHRSdata The latest set of IMU or MARG data [0-2] gyro, [3-5] accelerometer, {[6-8] magnetometer}
	*/
	public abstract void  AHRSUpdate(double[] AHRSdata);	
	
	/**
		Replaced Madgwick's sqrt(1/x) implementation with 1/Math.sqrt(x) since I don't need real time calculations, and due to the instability pointed out by Tobias Simon
		http://www.diydrones.com/forum/topics/madgwick-imu-ahrs-and-fast-inverse-square-root
		@param x the value for inverse square
	*/	
	public double invSqrt(double x){
		return 1/Math.sqrt(x);
	}

	/**
		sqrt(1/x) implementation pointed out by Tobias Simon, in case a more computationally efficient method is required
		http://www.diydrones.com/forum/topics/madgwick-imu-ahrs-and-fast-inverse-square-root
		@param x the value for inverse square
	*/	
	public static float invSqrt(float x){
		int i = 0x5F1F1412 - (Float.floatToIntBits(x) >> 1);
		float tmp = Float.intBitsToFloat(i);
		return tmp * (1.69000231f - 0.714158168f * x * tmp * tmp);
	}
	
}
