package deakin.timo.visualizeAxes;

import java.awt.BorderLayout;

import javax.media.opengl.*;
//import javax.media.opengl.awt.GLCanvas;		/*GLCanvas*/
import javax.media.opengl.awt.GLJPanel;		/*GLJPanel*/
import com.jogamp.opengl.util.*;			/*FPSAnimator*/
import javax.media.opengl.glu.*;			/*GL utilities, drawing tools?*/
import javax.swing.JFrame;
import java.io.*;					/*For texture*/
import com.jogamp.opengl.util.texture.*;		/*For texture*/
import java.io.IOException;					/*Error handling*/
import deakin.timo.visualizeAxes.utils.*;					/*Quaternions for rotations*/
import java.nio.*;							/*Bytebuffers*/
import com.jogamp.opengl.util.awt.TextRenderer;	/*Rendering text*/
import java.awt.Font;							/*Font for text*/
import java.awt.Dimension;						/*Setting size*/

/**
 * Modified from the: 
 *A minimal JOGL demo.
 * 
 * @author <a href="mailto:kain@land-of-kain.de">Kai Ruhl</a>
 * @since 26 Feb 2009
 
 Added earth texture from 
 http://visibleearth.nasa.gov/view.php?id=73580
 
 Kain suggested
 http://planetpixelemporium.com/earth.html
 which has an elevation map as well (interesting thought...)
 
 */
public class VisualizeAxes extends GLJPanel implements GLEventListener {

    
    private static final long serialVersionUID = 20140218L;	/** Serial version UID. */
    private GLU glu;										/** The GL unit (helper class). */
    private int fps = 60;									/** The frames per second setting. */
    private FPSAnimator animator=null;							/** The OpenGL animator. */
	private float rotationAngle;
	private int counter = 0;
	private int currentIndex = 0;
	private float[] rotationQuaternion = null;			/** Rotation quaternion*/
	//private float[][] rotationQuaternion = null;			/** Rotation quaternion*/
	private float[] tempRQ = null;
	Quaternion testQuat = null;
	private TextRenderer tRenderer = null;	/**Text to display position in quaternion array*/
    /**
     * A new mini starter.
     * 
     * @param capabilities The GL capabilities.
     * @param width The window width.
     * @param height The window height.
     */
    public VisualizeAxes(int width, int height) {
	   	super(new GLCapabilities(GLProfile.getDefault()));
		setPreferredSize(new Dimension(width,height));
        //setSize(width, height);
        addGLEventListener(this);
        rotationAngle = 0;
    }

	public void start(){
		Boolean isStarted = false;
		System.out.println("Starting");
		while (!isStarted){
			System.out.println("Trying to start "+Boolean.toString(isStarted));
			if (animator != null){
				animator.start();
				isStarted = true;
			}else{
				System.out.println("Animator null");
				try{
					Thread.sleep(100);
				}catch(Exception err){}
			}
			
		}
		
	}
	

	/*
	public void setRotationQuaternion(float[][] rotationQuaternion){
		this.rotationQuaternion = rotationQuaternion;
	}
	*/

	public void setRotationQuaternion(float[] rotationQuaternion){
		this.rotationQuaternion = rotationQuaternion;
	}
	
    /**
     * @return Some standard GL capabilities (with alpha).
     */
    private static GLCapabilities createGLCapabilities() {
        GLProfile glp = GLProfile.getDefault();//(GLProfile.GL2); /*My maching supports up to GL2...*/
        GLCapabilities capabilities = new GLCapabilities(glp);
        return capabilities;
    }

    /**
     * Sets up the screen.
     * 
     * @see javax.media.opengl.GLEventListener#init(javax.media.opengl.GLAutoDrawable)
     */
    public void init(GLAutoDrawable drawable) {
		System.out.println("vAxes init!!");
        final GL2 gl = drawable.getGL().getGL2();
        // Enable z- (depth) buffer for hidden surface removal. 
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);
        // Enable smooth shading.
        gl.glShadeModel(gl.GL_SMOOTH);
        // Define "clear" color.
        gl.glClearColor(0f, 0f, 0f, 0f);
        // We want a nice perspective.
        gl.glHint(gl.GL_PERSPECTIVE_CORRECTION_HINT, gl.GL_NICEST);
        // Create GLU.
        glu = new GLU();
		//Text
		tRenderer = new TextRenderer(new Font("Times", Font.BOLD,36));
        // Start animator.
        animator = new FPSAnimator(this, fps);
        
        
    }

    /**
     * The only method that you should implement by yourself.
     * 
     * @see javax.media.opengl.GLEventListener#display(javax.media.opengl.GLAutoDrawable)
     */
    public void display(GLAutoDrawable drawable) {
        if (!animator.isAnimating()) {
            return;
        }
        final GL2 gl = drawable.getGL().getGL2();

        // Clear screen.
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        // Set camera.
        setCamera(gl, glu, 5);

        /*Lighting*/
        float SHINE_ALL_DIRECTIONS = 1;
        float[] lightPos = {-30, 30, 30, SHINE_ALL_DIRECTIONS};
        float[] lightColorAmbient = {0.2f, 0.2f, 0.2f, 1f};
        float[] lightColorSpecular = {0.8f, 0.8f, 0.8f, 1f};

        // Set light parameters.
        gl.glLightfv(gl.GL_LIGHT1, gl.GL_POSITION, lightPos, 0);
        gl.glLightfv(gl.GL_LIGHT1, gl.GL_AMBIENT, lightColorAmbient, 0);
        gl.glLightfv(gl.GL_LIGHT1, gl.GL_SPECULAR, lightColorSpecular, 0);

        // Enable lighting in GL.
        gl.glEnable(gl.GL_LIGHT1);
        gl.glEnable(gl.GL_LIGHTING);

		/*Visualize the rotation*/
		double rotA = rotationAngle/180.0*Math.PI;
		
		if (rotationQuaternion == null){
			float[] rAx = new float[]{1f,0f,0f};
			if (counter >360 && counter <=720){
				rAx = new float[]{0f,1f,0f};
			}			
			if (counter >720){
				rAx = new float[]{0f,0f,1f};
			}
			/*Init testQuat*/
			if (testQuat == null){
				testQuat = new Quaternion(1d,0d,0d,0d);
				rotationAngle =1f;
				
			}else{
				Quaternion addRot = new Quaternion(Math.cos((rotationAngle/180f*(float)Math.PI)/2.0),-Math.sin((rotationAngle/180f*(float)Math.PI)/2.0)*rAx[0],-Math.sin((rotationAngle/180f*(float)Math.PI)/2.0)*rAx[1],-Math.sin((rotationAngle/180f*(float)Math.PI)/2.0)*rAx[2]);
				testQuat = addRot.times(testQuat);
			}
			
			
			tempRQ = new float[]{(float) (testQuat.x0),(float)(testQuat.x1),(float)(testQuat.x2),(float)(testQuat.x3)};
			//rotationAngle +=1f;
			++counter;
			if (counter > 1080){
				counter = 0;
			}
		}else{
				tempRQ = rotationQuaternion;
				++counter;
		}
		float[][] axes = {{90f,0f,1f,0f}, {-90f,1f,0f,0f},{0f,1f,0f,0f}};
		float[][] colours = {{1f,0f,0f}, {0f,1f,0f},{0f,0f,1f}};
		
		//System.out.println("w "+tempRQ[0]+" x "+tempRQ[1]+" y "+tempRQ[2]+" z "+tempRQ[3]);
		for (int i = 0; i<axes.length;++i){
			gl.glPushMatrix();	/*Save this state*/
			addArrow(gl, axes[i],tempRQ,colours[i]);	/*Add next axis arrow*/
			gl.glPopMatrix();	/*Return the stater*/
		}
		
		/*Add text*/
		int w =drawable.getWidth();
		int h = drawable.getHeight();
		tRenderer.beginRendering(w, h);
		// optionally set the color
		tRenderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		tRenderer.draw(String.format("%.2f",((double)currentIndex)/(double)fps), w/10, h/10);
		// ... more draw commands, color changes, etc.
		tRenderer.endRendering();
    }

	private void addArrow(GL2 gl, float[] axisRotation,float[] rotationQuaternion,float[] colours){
		/*Rotations are considered in the  local coordinate system*/
		/*Prepare a quaternion rotQuat to rotate the axis from being aligned with Z to the correct direction */
        double rotAngle = axisRotation[0]/180.0*Math.PI;
        Quaternion axisQuat = new Quaternion(Math.cos(rotAngle/2.0),Math.sin(rotAngle/2.0)*axisRotation[1],Math.sin(rotAngle/2.0)*axisRotation[2],Math.sin(rotAngle/2)*axisRotation[3]);
		/*Rotate the whole coordinate system around the x-axis*/
		Quaternion xQuat = new Quaternion(Math.cos(-Math.PI/2f/2f),Math.sin(-Math.PI/2f/2f)*1f,0f,0f);
		/*Rotation of the local coordinate system from the imu*/
		
		Quaternion localQuat = new Quaternion(rotationQuaternion[0],rotationQuaternion[1],rotationQuaternion[2],rotationQuaternion[3]);
		/*
		if ((counter % 60) == 0 && colours[0] == 1.0f){
			System.out.println(localQuat.toString());
		}
		*/
		/*Apply the rotations*/
		/*Combine the rotation to coordinate axes, and the local coordinate system 
rotation*/
		Quaternion oQuat = localQuat.times(axisQuat);
        Quaternion totalQ = xQuat.times(oQuat);	
	
        /*Apply the rotation to the visualization coordinate system*/
        gl.glRotatef((float) (Math.acos(totalQ.x0)*2.0/Math.PI*180.0),(float)(totalQ.x1),(float)(totalQ.x2),(float)(totalQ.x3));	/*Uses quaternion type expression*/ 

        // Set material properties.
        float[] rgba = {colours[0], colours[1], colours[2],1.0f};
        gl.glMaterialfv(gl.GL_FRONT, gl.GL_AMBIENT, rgba, 0);
        gl.glMaterialfv(gl.GL_FRONT, gl.GL_SPECULAR, rgba, 0);
        gl.glMaterialf(gl.GL_FRONT, gl.GL_SHININESS, 0.9f);
        
        // Draw arrow cylinder.
		/*Parameters for the cylinder*/
		final float base = 0.25f;		/*cylinder bottom radius*/
		final float top = 0.25f;		/*cylinder top radius*/
		final float height = 1f;		/*cylinder height*/
		/*How many quadrics to use to create the cylinder*/
        final int slices = 10;
        final int stacks = 10;
        GLUquadric axis = glu.gluNewQuadric();
        glu.gluQuadricDrawStyle(axis, GLU.GLU_FILL);
        glu.gluQuadricNormals(axis, GLU.GLU_FLAT);
        glu.gluQuadricOrientation(axis, GLU.GLU_OUTSIDE);
		glu.gluCylinder(axis, base,top, height, slices, stacks);
        glu.gluDeleteQuadric(axis);
		
		//Draw arrow head
        final float ahHeight = 0.5f;	/*Arrow head height*/
		final float ahBase = 0.4f;	/*Arrow base radius*/
		final float ahTop = 0.0f;		/*Arrow tip radius*/
		GLUquadric arrowHead = glu.gluNewQuadric();
        glu.gluQuadricDrawStyle(arrowHead, GLU.GLU_FILL);
        glu.gluQuadricNormals(arrowHead, GLU.GLU_FLAT);
        glu.gluQuadricOrientation(arrowHead, GLU.GLU_OUTSIDE);
		gl.glTranslatef(0f, 0f, height);	/*Translate the arrow head to the tip of the cylinder*/
        glu.gluCylinder(arrowHead, ahBase,ahTop, ahHeight, slices, stacks);
        glu.gluDeleteQuadric(arrowHead);
	}
	
    /**
     * Resizes the screen.
     * 
     * @see javax.media.opengl.GLEventListener#reshape(javax.media.opengl.GLAutoDrawable,
     *      int, int, int, int)
     */
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        final GL gl = drawable.getGL();
        gl.glViewport(0, 0, width, height);
    }

    /**
     * Changing devices is not supported.
     * 
     * @see javax.media.opengl.GLEventListener#displayChanged(javax.media.opengl.GLAutoDrawable,
     *      boolean, boolean)
     */
    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
        throw new UnsupportedOperationException("Changing display is not supported.");
    }

    /**
     * @param gl The GL context.
     * @param glu The GL unit.
     * @param distance The distance from the screen.
     */
    private void setCamera(GL2 gl, GLU glu, float distance) {
        // Change to projection matrix.
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glLoadIdentity();

        // Perspective.
        float widthHeightRatio = (float) getWidth() / (float) getHeight();
        glu.gluPerspective(45, widthHeightRatio, 1, 1000);
        glu.gluLookAt(0, 0, distance, 0, 0, 0, 0, 1, 0);

        // Change back to model view matrix.
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glLoadIdentity();
    }
    
    /*GLEventListener dispose*/
    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

}
