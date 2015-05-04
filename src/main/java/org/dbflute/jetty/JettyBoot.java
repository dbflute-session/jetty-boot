/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.dbflute.jetty;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author p1us2er0
 * @author jflute
 */
public class JettyBoot {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(JettyBoot.class);
    protected static final String DEFAULT_MARK_DIR = "/tmp/dbflute/jettyboot";

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final int port;
    protected final String contextPath;
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;

    protected Server server;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    public JettyBoot(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
    }

    public JettyBoot asDevelopment() {
        development = true;
        return this;
    }

    public JettyBoot browseOnDesktop() {
        assertDevelopmentState();
        browseOnDesktop = true;
        return this;
    }

    public JettyBoot suppressShutdownHook() {
        assertDevelopmentState();
        suppressShutdownHook = true;
        return this;
    }

    protected void assertDevelopmentState() {
        if (!development) {
            throw new IllegalStateException("The option is valid only when development: port=" + port);
        }
    }

    // ===================================================================================
    //                                                                               Boot
    //                                                                              ======
    public JettyBoot bootAwait() {
        startBoot();
        await();
        return this;
    }

    public JettyBoot startBoot() { // no wait
        logger.info("...Booting the Jetty: port={} contextPath={}", port, contextPath);
        if (development) {
            registerShutdownHook();
        }
        prepareServer();
        final URI uri = startServer();
        logger.info("Boot successful{}: uri={}", development ? " as development" : "", uri);
        if (development) {
            browseOnDesktop(uri);
        }
        return this;
    }

    protected void prepareServer() {
        final WebAppContext context = prepareWebAppContext();
        server = new Server(new InetSocketAddress(getServerHost(), port));
        server.setHandler(context);
    }

    protected String getServerHost() {
        return "localhost";
    }

    protected URI startServer() {
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("server start failed.", e);
        }
        return server.getURI();
    }

    protected WebAppContext prepareWebAppContext() {
        final URL warLocation = JettyBoot.class.getProtectionDomain().getCodeSource().getLocation();
        final String path;
        try {
            path = warLocation.toURI().getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("server start failed.", e);
        }
        final WebAppContext context = new WebAppContext();
        if (path.endsWith(".war")) {
            context.setWar(warLocation.toExternalForm());
        } else {
            context.setResourceBase(getResourceBase());
        }
        final Configuration[] configurations = prepareConfigurations();
        context.setConfigurations(configurations);
        context.setContextPath(contextPath);
        return context;
    }

    protected String getResourceBase() {
        return "./src/main/webapp/";
    }

    protected Configuration[] prepareConfigurations() {
        return new Configuration[] { new WebInfConfiguration(), new WebXmlConfiguration(), new MetaInfConfiguration(),
                new FragmentConfiguration(), new EnvConfiguration(), new JettyWebXmlConfiguration() };
    }

    // ===================================================================================
    //                                                                         Development
    //                                                                         ===========
    // -----------------------------------------------------
    //                                         Shutdown Hook
    //                                         -------------
    protected void registerShutdownHook() {
        if (suppressShutdownHook) {
            return;
        }
        final File markFile = prepareMarkFile();
        final long lastModified = markFile.lastModified();
        final String exp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date(lastModified));
        logger.info("...Registering the shutdown hook for the Jetty: lastModified=" + exp);
        new Thread(() -> {
            while (true) {
                if (needsShutdown(markFile, lastModified)) {
                    shutdownForcedly();
                    break;
                }
                waitForNextShuwdownHook();
            }
        }).start();
    }

    protected File prepareMarkFile() {
        final File markFile = new File(buildMarkFilePath());
        if (markFile.exists()) {
            markFile.setLastModified(System.currentTimeMillis());
        } else {
            markFile.mkdirs();
            try {
                markFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create new file: " + markFile, e);
            }
        }
        return markFile;
    }

    protected String buildMarkFilePath() {
        return getMarkDir() + "/boot" + port + ".dfmark";
    }

    protected String getMarkDir() {
        return DEFAULT_MARK_DIR;
    }

    protected boolean needsShutdown(File markFile, long lastModified) {
        return !markFile.exists() || lastModified != markFile.lastModified();
    }

    protected void shutdownForcedly() {
        logger.info("...Shuting down the Jetty forcedly: port=" + port);
        close();
    }

    protected void waitForNextShuwdownHook() {
        try {
            Thread.sleep(getShuwdownHookWaitMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException("Failed to sleep the thread.", e);
        }
    }

    protected long getShuwdownHookWaitMillis() {
        return 2000L;
    }

    // -----------------------------------------------------
    //                                                Browse
    //                                                ------
    protected void browseOnDesktop(final URI uri) {
        if (!browseOnDesktop) {
            return;
        }
        final java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
        try {
            desktop.browse(uri);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to browse the URI: " + uri, e);
        }
    }

    // ===================================================================================
    //                                                                               Await
    //                                                                               =====
    public void await() {
        if (server == null) {
            throw new IllegalStateException("server has not been started.");
        }
        try {
            server.join();
        } catch (Exception e) {
            throw new IllegalStateException("server join failed.", e);
        }
    }

    // ===================================================================================
    //                                                                               Close
    //                                                                               =====
    public void close() {
        if (server == null) {
            throw new IllegalStateException("server has not been started.");
        }
        try {
            server.stop();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop the Jetty.", e);
        } finally {
            try {
                server.destroy();
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to destroy the Jetty.", e);
            }
        }
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Server getServer() {
        return server;
    }
}