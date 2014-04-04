/*************************************************************************
 *  Compilation:  javac Quaternion.java
 *  Execution:    java Quaternion
 *
 *  Data type for quaternions.
 *
 *  http://mathworld.wolfram.com/Quaternion.html
 *
 *  The data type is "immutable" so once you create and initialize
 *  a Quaternion, you cannot change it.
 *
 *  % java Quaternion
 *
 *************************************************************************/

package deakin.timo.visualizeAxes.utils;

public class Quaternion {
    public final double x0, x1, x2, x3; 

    // create a new object with the given components
    public Quaternion(double x0, double x1, double x2, double x3) {
        this.x0 = x0;
        this.x1 = x1;
        this.x2 = x2;
        this.x3 = x3;
    }
	
	public double[] getAxis(){
		double[] axis = new double[3];
		axis[0] = x1;
		axis[1] = x2;
		axis[2] = x3;
		return axis;		
	}
	public float[] getFloat(){
		float[] a = new float[4];
		a[0] = (float) x0;
		a[1] = (float) x1;
		a[2] = (float) x2;
		a[3] = (float) x3;
		return a;		
	}
	
	public double[] getDouble(){
		double[] a = new double[4];
		a[0] = x0;
		a[1] = x1;
		a[2] = x2;
		a[3] = x3;
		return a;		
	}
	
    // return a string representation of the invoking object
    public String toString() {
        return String.format("w%.3f x%.3f y%.3f x%.3f",x0,x1,x2,x3);//..x0 + " + " + x1 + "i + " + x2 + "j + " + x3 + "k";
    }

	
	public Quaternion getUnitQuaternion(){
		return new Quaternion(this.x0/this.norm(),this.x1/this.norm(),this.x2/this.norm(),this.x3/this.norm());
	}
    // return the quaternion norm
    public double norm() {
        return Math.sqrt(x0*x0 + x1*x1 +x2*x2 + x3*x3);
    }
	
	//Return unit quaternion
	public Quaternion getUnit(){
		double magnitude = this.norm();
		return new Quaternion(this.x0/magnitude, this.x1/magnitude, this.x2/magnitude, this.x3/magnitude);
	}
	
    // return the quaternion conjugate
    public Quaternion conjugate() {
        return new Quaternion(x0, -x1, -x2, -x3);
    }

    // return a new Quaternion whose value is (this + b)
    public Quaternion plus(Quaternion b) {
        Quaternion a = this;
        return new Quaternion(a.x0+b.x0, a.x1+b.x1, a.x2+b.x2, a.x3+b.x3);
    }


    // return a new Quaternion whose value is (this * b)
    public Quaternion times(Quaternion b) {
        Quaternion a = this;
        double y0 = a.x0*b.x0 - a.x1*b.x1 - a.x2*b.x2 - a.x3*b.x3;
        double y1 = a.x0*b.x1 + a.x1*b.x0 + a.x2*b.x3 - a.x3*b.x2;
        double y2 = a.x0*b.x2 - a.x1*b.x3 + a.x2*b.x0 + a.x3*b.x1;
        double y3 = a.x0*b.x3 + a.x1*b.x2 - a.x2*b.x1 + a.x3*b.x0;
        return new Quaternion(y0, y1, y2, y3);
    }

    // return a new Quaternion whose value is the inverse of this
    public Quaternion inverse() {
        double d = x0*x0 + x1*x1 + x2*x2 + x3*x3;
        return new Quaternion(x0/d, -x1/d, -x2/d, -x3/d);
    }


    // return a / b
    public Quaternion divides(Quaternion b) {
         Quaternion a = this;
        return a.inverse().times(b);
    }

    // sample client for testing
    public static void main(String[] args) {
        Quaternion a = new Quaternion(3.0, 1.0, 0.0, 0.0);
        System.out.println("a = " + a);

        Quaternion b = new Quaternion(0.0, 5.0, 1.0, -2.0);
        System.out.println("b = " + b);

        System.out.println("norm(a)  = " + a.norm());
        System.out.println("conj(a)  = " + a.conjugate());
        System.out.println("a + b    = " + a.plus(b));
        System.out.println("a * b    = " + a.times(b));
        System.out.println("b * a    = " + b.times(a));
        System.out.println("a / b    = " + a.divides(b));
        System.out.println("a^-1     = " + a.inverse());
        System.out.println("a^-1 * a = " + a.inverse().times(a));
        System.out.println("a * a^-1 = " + a.times(a.inverse()));
    }

}
