/**
 * Copyright 2013 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
 
package com.jogamp.opengl.test.junit.jogl.awt;

import javax.media.opengl.*;

import com.jogamp.opengl.util.Animator;

import javax.media.opengl.awt.GLCanvas;
import javax.swing.BoxLayout;
import javax.swing.JFrame;

import com.jogamp.newt.event.awt.AWTWindowAdapter;
import com.jogamp.newt.event.TraceWindowAdapter;
import com.jogamp.opengl.test.junit.jogl.demos.es2.GearsES2;
import com.jogamp.opengl.test.junit.jogl.demos.es2.RedSquareES2;
import com.jogamp.opengl.test.junit.util.AWTRobotUtil;
import com.jogamp.opengl.test.junit.util.MiscUtils;
import com.jogamp.opengl.test.junit.util.UITestCase;
import com.jogamp.opengl.test.junit.util.QuitAdapter;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestBug816OSXCALayerPosAWT extends UITestCase {
    public enum FrameLayout { None, Flow, DoubleBorderCenterSurrounded, Box };
    
    static long duration = 500; // ms    
    static int width, height;
    
    static boolean forceES2 = false;
    static boolean forceGL3 = false;
    static int swapInterval = 1;
    static Thread awtEDT;
    static java.awt.Dimension rwsize;

    @BeforeClass
    public static void initClass() {
        width  = 640;
        height = 480;
        rwsize = new Dimension(800, 600);
        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    awtEDT = Thread.currentThread();
                } } );
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertNull(e);
        }
    }

    @AfterClass
    public static void releaseClass() {
    }

    static void setComponentSize(final Frame frame, final Component comp1, final java.awt.Dimension new_sz1, final Component comp2, final java.awt.Dimension new_sz2) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    comp1.setMinimumSize(new_sz1);
                    comp1.setPreferredSize(new_sz1);
                    comp1.setSize(new_sz1);
                    if( null != comp2 ) {
                        comp2.setMinimumSize(new_sz2);
                        comp2.setPreferredSize(new_sz2);
                        comp2.setSize(new_sz2);
                    }
                    if( null != frame ) {
                        frame.pack();
                    }
                } } );
        } catch( Throwable throwable ) {
            throwable.printStackTrace();
            Assume.assumeNoException( throwable );
        }       
    }
    
    protected void runTestGL(GLCapabilities caps, FrameLayout frameLayout, final boolean twoCanvas) throws InterruptedException, InvocationTargetException {
        final JFrame frame = new JFrame("Bug861 AWT Test");
        Assert.assertNotNull(frame);
        final Container framePane = frame.getContentPane();

        final GLCanvas glCanvas1 = new GLCanvas(caps);
        Assert.assertNotNull(glCanvas1);
        final GLCanvas glCanvas2;
        if( twoCanvas ) {
            glCanvas2 = new GLCanvas(caps);
            Assert.assertNotNull(glCanvas2);
        } else {
            glCanvas2 = null;
        }
        
        final Dimension glcDim = new Dimension(width/2, height);
        
        setComponentSize(null, glCanvas1, glcDim, glCanvas2, glcDim);
        
        switch( frameLayout) {
            case None: {
                    framePane.add(glCanvas1);
                }
                break;
            case Flow: {
                    final Container c = new Container();
                    c.setLayout(new FlowLayout());
                    c.add(glCanvas1);
                    if( twoCanvas ) {
                        c.add(glCanvas2);
                    }
                    framePane.add(c);
                }
                break;
            case DoubleBorderCenterSurrounded: {
                    final Container c = new Container();
                    c.setLayout(new BorderLayout());
                    c.add(new Button("north"), BorderLayout.NORTH);
                    c.add(new Button("south"), BorderLayout.SOUTH);
                    c.add(new Button("east"), BorderLayout.EAST);
                    c.add(new Button("west"), BorderLayout.WEST);
                    if( twoCanvas ) {
                        final Container c2 = new Container();
                        c2.setLayout(new GridLayout(1, 2));
                        c2.add(glCanvas1);
                        c2.add(glCanvas2);
                        c.add(c2, BorderLayout.CENTER);
                    } else {
                        c.add(glCanvas1, BorderLayout.CENTER);
                    }
                    framePane.setLayout(new BorderLayout());
                    framePane.add(new Button("NORTH"), BorderLayout.NORTH);
                    framePane.add(new Button("SOUTH"), BorderLayout.SOUTH);
                    framePane.add(new Button("EAST"), BorderLayout.EAST);
                    framePane.add(new Button("WEST"), BorderLayout.WEST);
                    framePane.add(c, BorderLayout.CENTER);
                }
                break;
            case Box: {
                    final Container c = new Container();
                    c.setLayout(new BoxLayout(c, BoxLayout.X_AXIS));
                    c.add(glCanvas1);
                    if( twoCanvas ) {
                        c.add(glCanvas2);
                    }
                    framePane.add(c);
                }
                break;
        }
        final GearsES2 demo1 = new GearsES2(swapInterval);
        glCanvas1.addGLEventListener(demo1);
        if( twoCanvas ) {
            final RedSquareES2 demo2 = new RedSquareES2(swapInterval);
            glCanvas2.addGLEventListener(demo2);
        }
        
        final Animator animator = new Animator();
        animator.add(glCanvas1);
        if( twoCanvas ) {
            animator.add(glCanvas2);
        }
        QuitAdapter quitAdapter = new QuitAdapter();

        new AWTWindowAdapter(new TraceWindowAdapter(quitAdapter)).addTo(frame);

        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
               frame.pack();                   
               frame.setVisible(true);
            }});        
        Assert.assertEquals(true,  AWTRobotUtil.waitForVisible(frame, true));
        Assert.assertEquals(true,  AWTRobotUtil.waitForRealized(glCanvas1, true)); 
        if( twoCanvas ) {
            Assert.assertEquals(true,  AWTRobotUtil.waitForRealized(glCanvas2, true));
        }
        
        animator.start();
        Assert.assertTrue(animator.isStarted());
        Assert.assertTrue(animator.isAnimating());
        animator.setUpdateFPSFrames(60, System.err);
        
        System.err.println("canvas1 pos/siz: "+glCanvas1.getX()+"/"+glCanvas1.getY()+" "+glCanvas1.getWidth()+"x"+glCanvas1.getHeight());
        if( twoCanvas ) {
            System.err.println("canvas2 pos/siz: "+glCanvas2.getX()+"/"+glCanvas2.getY()+" "+glCanvas2.getWidth()+"x"+glCanvas2.getHeight());
        }

        Thread.sleep(Math.max(1000, duration/2));
        final Dimension rwsizeHalf = new Dimension(rwsize.width/2, rwsize.height);
        setComponentSize(frame, glCanvas1, rwsizeHalf, glCanvas2, rwsizeHalf);
        System.err.println("resize canvas1 pos/siz: "+glCanvas1.getX()+"/"+glCanvas1.getY()+" "+glCanvas1.getWidth()+"x"+glCanvas1.getHeight());
        if( twoCanvas ) {
            System.err.println("resize canvas2 pos/siz: "+glCanvas2.getX()+"/"+glCanvas2.getY()+" "+glCanvas2.getWidth()+"x"+glCanvas2.getHeight());
        }
        
        final long t0 = System.currentTimeMillis();
        long t1 = t0;
        while(!quitAdapter.shouldQuit() && t1 - t0 < duration) {
            Thread.sleep(100);
            t1 = System.currentTimeMillis();
        }

        Assert.assertNotNull(frame);
        Assert.assertNotNull(glCanvas1);
        if( twoCanvas ) {
            Assert.assertNotNull(glCanvas2);
        } else {
            Assert.assertNull(glCanvas2);
        }
        
        Assert.assertNotNull(animator);
        animator.stop();
        Assert.assertFalse(animator.isAnimating());
        Assert.assertFalse(animator.isStarted());

        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                frame.setVisible(false);
            }});
        Assert.assertEquals(false, frame.isVisible());
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                frame.remove(glCanvas1);
                if( twoCanvas ) {
                    frame.remove(glCanvas2);
                }
                frame.dispose();
            }});
    }

    static GLProfile getGLP() {
        return GLProfile.getMaxProgrammableCore(true);
    }
    
    @Test
    public void test00_None_One() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 0 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.None, false);
    }
    
    @Test
    public void test01_Flow_One() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 1 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.Flow, false);
    }

    @Test
    public void test02_DblBrd_One() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 2 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.DoubleBorderCenterSurrounded, false);
    }
    
    @Test
    public void test03_Box_One() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 3 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.Box, false);
    }
    
    @Test
    public void test04_Flow_Two() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 4 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.Flow, true);
    }

    @Test
    public void test05_DblBrd_Two() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 5 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.DoubleBorderCenterSurrounded, true);
    }
    
    @Test
    public void test06_Box_Two() throws InterruptedException, InvocationTargetException {
        if( testNum != -1 && testNum != 6 ) { return ; }
        final GLCapabilities caps = new GLCapabilities(getGLP());
        runTestGL(caps, FrameLayout.Box, true);
    }
    
    static int testNum = -1;
    
    public static void main(String args[]) {
        boolean waitForKey = false;
        
        for(int i=0; i<args.length; i++) {
            if(args[i].equals("-time")) {
                i++;
                duration = MiscUtils.atol(args[i], duration);
            } else if(args[i].equals("-test")) {
                i++;
                testNum = MiscUtils.atoi(args[i], 0);
            } else if(args[i].equals("-es2")) {
                forceES2 = true;
            } else if(args[i].equals("-gl3")) {
                forceGL3 = true;
            } else if(args[i].equals("-vsync")) {
                i++;
                swapInterval = MiscUtils.atoi(args[i], swapInterval);
            }
        }
        
        System.err.println("resize "+rwsize);
        System.err.println("forceES2 "+forceES2);
        System.err.println("forceGL3 "+forceGL3);
        System.err.println("swapInterval "+swapInterval);
        
        if(waitForKey) {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            System.err.println("Press enter to continue");
            try {
                System.err.println(stdin.readLine());
            } catch (IOException e) { }
        }
        org.junit.runner.JUnitCore.main(TestBug816OSXCALayerPosAWT.class.getName());
    }
}