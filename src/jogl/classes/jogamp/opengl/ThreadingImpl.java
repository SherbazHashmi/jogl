/*
 * Copyright (c) 2009 Sun Microsystems, Inc. All Rights Reserved.
 * Copyright (c) 2010 JogAmp Community. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 */

package jogamp.opengl;

import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import com.jogamp.common.JogampRuntimeException;
import com.jogamp.common.util.*;
import javax.media.nativewindow.NativeWindowFactory;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;

/** Implementation of the {@link javax.media.opengl.Threading} class. */

public class ThreadingImpl {
    public static final int AWT    = 1;
    public static final int WORKER = 2;

    protected static final boolean DEBUG = Debug.debug("Threading");

    private static boolean singleThreaded = true;
    private static int mode;
    private static boolean hasAWT;
    // We need to know whether we're running on X11 platforms to change
    // our behavior when the Java2D/JOGL bridge is active
    private static boolean _isX11;

    private static final ThreadingPlugin threadingPlugin;
  
    static {
        threadingPlugin =
            AccessController.doPrivileged(new PrivilegedAction<ThreadingPlugin>() {
                    public ThreadingPlugin run() {
                        String workaround = Debug.getProperty("jogl.1thread", true);
                        ClassLoader cl = ThreadingImpl.class.getClassLoader();
                        // Default to using the AWT thread on all platforms except
                        // Windows. On OS X there is instability apparently due to
                        // using the JAWT on non-AWT threads. On X11 platforms there
                        // are potential deadlocks which can be caused if the AWT
                        // EventQueue thread hands work off to the GLWorkerThread
                        // while holding the AWT lock. The optimization of
                        // makeCurrent / release calls isn't worth these stability
                        // problems.
                        hasAWT = GLProfile.isAWTAvailable();

                        String osType = NativeWindowFactory.getNativeWindowType(false);
                        _isX11 = NativeWindowFactory.TYPE_X11.equals(osType);

                        int defaultMode = ( hasAWT ? AWT : WORKER );

                        mode = defaultMode;
                        if (workaround != null) {
                            workaround = workaround.toLowerCase();
                            if (workaround.equals("true") ||
                                workaround.equals("auto")) {
                                // Nothing to do; singleThreaded and mode already set up
                            } else if (workaround.equals("worker")) {
                                singleThreaded = true;
                                mode = WORKER;
                            } else if (hasAWT && workaround.equals("awt")) {
                                singleThreaded = true;
                                mode = AWT;
                            } else {
                                singleThreaded = false;
                            }
                        }
                        printWorkaroundNotice();

                        ThreadingPlugin threadingPlugin=null;
                        if(hasAWT) {
                            // try to fetch the AWTThreadingPlugin
                            Exception error=null;
                            try {
                                threadingPlugin = (ThreadingPlugin) ReflectionUtil.createInstance("jogamp.opengl.awt.AWTThreadingPlugin", cl);
                            } catch (JogampRuntimeException jre) { error = jre; }
                            if(AWT == mode && null==threadingPlugin) {                                
                                throw new GLException("Mode is AWT, but class 'jogamp.opengl.awt.AWTThreadingPlugin' is not available", error);
                            }
                        }
                        return threadingPlugin;
                    }
                });
        if(DEBUG) {
            System.err.println("Threading: hasAWT "+hasAWT+", mode "+((mode==AWT)?"AWT":"WORKER")+", plugin "+threadingPlugin);
        }
    }

    /** No reason to ever instantiate this class */
    private ThreadingImpl() {}

    public static boolean isX11() { return _isX11; }
    public static int getMode() { return mode; }

    /** If an implementation of the javax.media.opengl APIs offers a 
        multithreading option but the default behavior is single-threading, 
        this API provides a mechanism for end users to disable single-threading 
        in this implementation.  Users are strongly discouraged from
        calling this method unless they are aware of all of the
        consequences and are prepared to enforce some amount of
        threading restrictions in their applications. Disabling
        single-threading, for example, may have unintended consequences
        on GLAutoDrawable implementations such as GLCanvas, GLJPanel and
        GLPbuffer. Currently there is no supported way to re-enable it
        once disabled, partly to discourage careless use of this
        method. This method should be called as early as possible in an
        application. */ 
    public static void disableSingleThreading() {
        singleThreaded = false;
        if (Debug.verbose()) {
            System.err.println("Application forced disabling of single-threading of javax.media.opengl implementation");
        }
    }

    /** Indicates whether OpenGL work is being automatically forced to a
        single thread in this implementation. */
    public static boolean isSingleThreaded() {
        return singleThreaded;
    }

    /** Indicates whether the current thread is the single thread on
        which this implementation of the javax.media.opengl APIs
        performs all of its OpenGL-related work. This method should only
        be called if the single-thread model is in effect. */
    public static boolean isOpenGLThread() throws GLException {
        if (!isSingleThreaded()) {
            throw new GLException("Should only call this in single-threaded mode");
        }

        if(null!=threadingPlugin) {
            return threadingPlugin.isOpenGLThread();
        }

        switch (mode) {
            case AWT:
                throw new InternalError();
            case WORKER:
                return GLWorkerThread.isWorkerThread();
            default:
                throw new InternalError("Illegal single-threading mode " + mode);
        }
    }

    /** Executes the passed Runnable on the single thread used for all
        OpenGL work in this javax.media.opengl API implementation. It is
        not specified exactly which thread is used for this
        purpose. This method should only be called if the single-thread
        model is in use and if the current thread is not the OpenGL
        thread (i.e., if <code>isOpenGLThread()</code> returns
        false). It is up to the end user to check to see whether the
        current thread is the OpenGL thread and either execute the
        Runnable directly or perform the work inside it. */
    public static void invokeOnOpenGLThread(Runnable r) throws GLException {
        if (!isSingleThreaded()) {
            throw new GLException ("Should only call this in single-threaded mode");
        }

        if (isOpenGLThread()) {
            throw new GLException ("Should only call this from other threads than the OpenGL thread");
        }    

        if(null!=threadingPlugin) {
            threadingPlugin.invokeOnOpenGLThread(r);
            return;
        }

        switch (mode) {
            case AWT:
                throw new InternalError();

            case WORKER:
                GLWorkerThread.start(); // singleton start via volatile-dbl-checked-locking
                try {
                    GLWorkerThread.invokeAndWait(r);
                } catch (InvocationTargetException e) {
                    throw new GLException(e.getTargetException());
                } catch (InterruptedException e) {
                    throw new GLException(e);
                }
                break;

            default:
                throw new InternalError("Illegal single-threading mode " + mode);
        }
    }

    /** This is a workaround for AWT-related deadlocks which only seem
        to show up in the context of applets */
    public static boolean isAWTMode() {
        return (mode == AWT);
    }

    private static void printWorkaroundNotice() {
        if (singleThreaded && Debug.verbose()) {
            System.err.println("Using " +
                               (mode == AWT ? "AWT" : "OpenGL worker") +
                               " thread for performing OpenGL work in javax.media.opengl implementation");
        }
    }
}
