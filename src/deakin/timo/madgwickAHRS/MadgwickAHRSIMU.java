/**
	Port of Seb Madqwick's open source IMU AHRS C implementation http://www.x-io.co.uk/open-source-imu-and-ahrs-algorithms/
	Decided to changed to double precision instead of float. Don't know whether it matters.
	Written by Timo Rantalainen 2014 tjrantal at gmail dot com
	Licensed with GPL 3.0 (https://www.gnu.org/copyleft/gpl.html)
*/
package deakin.timo.madgwickAHRS;
public class MadgwickAHRSIMU extends MadgwickAHRS{
	/**
		Constructor
		@param beta algorithm gain
		@param q orientation quaternion
		@param samplingFreq Sampling frequency
	*/
	public MadgwickAHRSIMU(double beta, double[] q, double samplingFreq){
		super(beta,q,samplingFreq);
	}
	
	/**
		Update the orientation according to the latest set of IMU measurements
		@param AHRSdata The latest set of IMU or MARG data [0-2] gyro, [3-5] accelerometer
	*/
	@Override
	public void  AHRSUpdate(double[] AHRSdata){
		double recipNorm;
		double[] s		= new double[4];
		double[] qDot	= new double[4];
		double[] _2q	= new double[4];
		double[] _4q	= new double[3];
		double[] _8q	= new double[2];
		double[] qq		= new double[4];
		
		// Rate of change of quaternion from gyroscope
		qDot[0] = 0.5d * (-q[1] * AHRSdata[0] - q[2] * AHRSdata[1] - q[3] * AHRSdata[2]);
		qDot[1] = 0.5d * (q[0] * AHRSdata[0] + q[2] * AHRSdata[2] - q[3] * AHRSdata[1]);
		qDot[2] = 0.5d * (q[0] * AHRSdata[1] - q[1] * AHRSdata[2] + q[3] * AHRSdata[0]);
		qDot[3] = 0.5d * (q[0] * AHRSdata[2] + q[1] * AHRSdata[1] - q[2] * AHRSdata[0]);

		// Compute feedback only if accelerometer measurement valid (avoids NaN in accelerometer normalisation)
		if(!((AHRSdata[3] == 0d) && (AHRSdata[4] == 0d) && (AHRSdata[5] == 0d))) {

			// Normalise accelerometer measurement
			recipNorm	= invSqrt(AHRSdata[3] * AHRSdata[3] + AHRSdata[4] * AHRSdata[4] + AHRSdata[5] * AHRSdata[5]);
			AHRSdata[3]	*= recipNorm;
			AHRSdata[4]	*= recipNorm;
			AHRSdata[5]	*= recipNorm;   

			// Auxiliary variables to avoid repeated arithmetic
			_2q[0] = 2d * q[0];
			_2q[1] = 2d * q[1];
			_2q[2] = 2d * q[2];
			_2q[3] = 2d * q[3];
			_4q[0] = 4d * q[0];
			_4q[1] = 4d * q[1];
			_4q[2] = 4d * q[2];
			_8q[0] = 8d * q[1];
			_8q[1] = 8d * q[2];
			qq[0] = q[0] * q[0];
			qq[1] = q[1] * q[1];
			qq[2] = q[2] * q[2];
			qq[3] = q[3] * q[3];

			// Gradient decent algorithm corrective step
			s[0] = _4q[0] * qq[2] + _2q[2] * AHRSdata[3] + _4q[0] * qq[1] - _2q[1] * AHRSdata[4];
			s[1] = _4q[1] * qq[3] - _2q[3] * AHRSdata[3] + 4.0f * qq[0] * q[1] - _2q[0] * AHRSdata[4] - _4q[1] + _8q[0] * qq[1] + _8q[0] * qq[2] + _4q[1] * AHRSdata[5];
			s[2] = 4.0f * qq[0] * q[2] + _2q[0] * AHRSdata[3] + _4q[2] * qq[3] - _2q[3] * AHRSdata[4] - _4q[2] + _8q[1] * qq[1] + _8q[1] * qq[2] + _4q[2] * AHRSdata[5];
			s[3] = 4.0f * qq[1] * q[3] - _2q[1] * AHRSdata[3] + 4.0f * qq[2] * q[3] - _2q[2] * AHRSdata[4];
			recipNorm = invSqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2] + s[3] * s[3]); // normalise step magnitude
			s[0] *= recipNorm;
			s[1] *= recipNorm;
			s[2] *= recipNorm;
			s[3] *= recipNorm;

			// Apply feedback step
			qDot[0] -= beta * s[0];
			qDot[1] -= beta * s[1];
			qDot[2] -= beta * s[2];
			qDot[3] -= beta * s[3];
		}

		// Integrate rate of change of quaternion to yield quaternion
		q[0] += qDot[0] * (1.0f / samplingFreq);
		q[1] += qDot[1] * (1.0f / samplingFreq);
		q[2] += qDot[2] * (1.0f / samplingFreq);
		q[3] += qDot[3] * (1.0f / samplingFreq);

		// Normalise quaternion
		recipNorm = invSqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
		q[0] *= recipNorm;
		q[1] *= recipNorm;
		q[2] *= recipNorm;
		q[3] *= recipNorm;	
	}
}