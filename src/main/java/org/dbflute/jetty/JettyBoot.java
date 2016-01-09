/*
 * Copyright 2015-2016 the original author or authors.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dbflute.jetty.util.BoJtResourceUtil;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * @author p1us2er0
 * @author jflute
 */
public class JettyBoot {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    protected static final String WEBROOT_RESOURCE_PATH = "/webroot/";
    protected static final String DEFAULT_MARK_DIR = "/tmp/dbflute/jettyboot"; // for shutdown hook

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                                 Basic
    //                                                 -----
    protected final int port;
    protected final String contextPath;

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    protected boolean development;
    protected boolean browseOnDesktop;
    protected boolean suppressShutdownHook;
    protected boolean useEmbeddedWebroot; // default is meven convention way
    protected boolean useAnnotationDetect;
    protected boolean useMetaInfoResourceDetect;
    protected boolean useTldDetect;
    protected boolean useWebFragmentsDetect;

    // -----------------------------------------------------
    //                                              Stateful
    //                                              --------
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

    public JettyBoot asDevelopment(boolean development) {
        this.development = development;
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

    public JettyBoot useEmbeddedWebroot() {
        useEmbeddedWebroot = true;
        return this;
    }

    public JettyBoot useAnnotationDetect() {
        useAnnotationDetect = true;
        return this;
    }

    public JettyBoot useMetaInfoResourceDetect() {
        useMetaInfoResourceDetect = true;
        return this;
    }

    public JettyBoot useTldDetect() {
        useTldDetect = true;
        return this;
    }

    public JettyBoot useWebFragmentsDetect() {
        useWebFragmentsDetect = true;
        return this;
    }

    // ===================================================================================
    //                                                                               Boot
    //                                                                              ======
    public JettyBoot bootAwait() {
        go();
        await();
        return this;
    }

    // -----------------------------------------------------
    //                                                  Go
    //                                                ------
    public JettyBoot go() { // public as parts, no wait
        info("...Booting the Jetty: port=" + port + " contextPath=" + contextPath);
        if (development) {
            registerShutdownHook();
        }
        prepareServer();
        final URI uri = startServer();
        info("Boot successful" + (development ? " as development" : "") + ": url -> " + uri);
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
        final URL warLocation = getWarLocation();
        final String path;
        try {
            path = warLocation.toURI().getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("server start failed.", e);
        }
        final WebAppContext context = new WebAppContext();
        if (path != null && path.endsWith(".war")) {
            context.setWar(warLocation.toExternalForm());
        } else {
            context.setResourceBase(getResourceBase());
        }
        context.setConfigurations(prepareConfigurations());
        context.setContextPath(contextPath);
        return context;
    }

    // -----------------------------------------------------
    //                                          War Location
    //                                          ------------
    protected URL getWarLocation() {
        return JettyBoot.class.getProtectionDomain().getCodeSource().getLocation();
    }

    // -----------------------------------------------------
    //                                         Resource Base
    //                                         -------------
    protected String getResourceBase() {
        if (useEmbeddedWebroot) { // option
            final String path = WEBROOT_RESOURCE_PATH;
            final URL webroot = getClass().getResource(path);
            if (webroot == null) {
                throw new IllegalStateException("Not found the webroot resource: path=" + path);
            }
            try {
                return webroot.toURI().toASCIIString();
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Illegal URL: " + webroot, e);
            }
        } else { // default is here
            return deriveWebappDir().getPath();
        }
    }

    protected File deriveWebappDir() {
        final String webappRelativePath = getBasicWebappRelativePath();
        final File webappDir = new File(webappRelativePath);
        if (webappDir.exists()) { // from current directory
            return webappDir;
        }
        final File projectWebappDir = findProjectWebappDir(webappRelativePath); // from build path
        if (projectWebappDir != null) {
            return projectWebappDir;
        }
        throw new IllegalStateException("Not found the webapp directory: " + webappDir);
    }

    protected String getBasicWebappRelativePath() {
        return "./src/main/webapp";
    }

    protected File findProjectWebappDir(String webappRelativePath) {
        info("...Finding project webapp from stack trace: webappRelativePath=" + webappRelativePath);
        final StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) { // just in case
            info("*Not found the stack trace: " + stackTrace);
            return null;
        }
        // IntelliJ calls from own main() so find nearest main()
        StackTraceElement rootElement = null;
        for (int i = 0; i < stackTrace.length; i++) {
            final StackTraceElement element = stackTrace[i];
            if ("main".equals(element.getMethodName())) {
                rootElement = element;
                break;
            }
        }
        if (rootElement == null) { // just in case
            info("*Not found the main method: " + Stream.of(stackTrace).map(el -> {
                return el.getMethodName();
            }).collect(Collectors.joining(",")));
            return null;
        }
        final String className = rootElement.getClassName(); // e.g. DocksideBoot
        final Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException continued) {
            info("*Not found the class: " + className + " :: " + continued.getMessage());
            return null;
        }
        final File buildDir = BoJtResourceUtil.getBuildDir(clazz); // target/classes
        final File targetDir = buildDir.getParentFile(); // target
        if (targetDir == null) { // just in case
            info("*Not found the target directory: buildDir=" + buildDir);
            return null;
        }
        final File projectDir = targetDir.getParentFile(); // e.g. maihama-dockside
        if (projectDir == null) { // just in case
            info("*Not found the project directory: targetDir=" + targetDir);
            return null;
        }
        final String projectPath;
        try {
            projectPath = projectDir.getCanonicalPath().replace("\\", "/");
        } catch (IOException continued) {
            info("*Cannot get canonical path from: " + projectDir + " :: " + continued.getMessage());
            return null;
        }
        final String projectWebappPath = projectPath + "/" + webappRelativePath;
        final File projectWebappDir = new File(projectWebappPath);
        if (projectWebappDir.exists()) {
            info("OK, found the project webapp: " + projectWebappPath);
            return projectWebappDir;
        } else {
            info("*Not found the project webapp by derived path: " + projectWebappPath);
            return null;
        }
    }

    // -----------------------------------------------------
    //                                        Configurations
    //                                        --------------
    protected Configuration[] prepareConfigurations() {
        final List<Configuration> configList = new ArrayList<Configuration>();
        setupConfigList(configList);
        return configList.toArray(new Configuration[configList.size()]);
    }

    protected void setupConfigList(List<Configuration> configList) {
        configList.add(createWebInfConfiguration());
        configList.add(createWebXmlConfiguration());
        if (isValidMetaInfConfiguration()) {
            configList.add(createMetaInfConfiguration());
        }
        if (isValidAnnotationConfiguration()) {
            configList.add(createAnnotationConfiguration());
        }
        if (isValidFragmentConfiguration()) {
            configList.add(createFragmentConfiguration());
        }
        configList.add(createEnvConfiguration());
        configList.add(createJettyWebXmlConfiguration());
    }

    protected WebInfConfiguration createWebInfConfiguration() {
        return new WebInfConfiguration();
    }

    protected WebXmlConfiguration createWebXmlConfiguration() {
        return new WebXmlConfiguration();
    }

    protected boolean isValidMetaInfConfiguration() {
        return useMetaInfoResourceDetect || useTldDetect || useWebFragmentsDetect;
    }

    protected MetaInfConfiguration createMetaInfConfiguration() {
        return new MetaInfConfiguration();
    }

    protected boolean isValidAnnotationConfiguration() {
        // unneede for tld at 9.2
        //return useAnnotationDetect || useTldDetect;
        return useAnnotationDetect;
    }

    protected AnnotationConfiguration createAnnotationConfiguration() {
        return new AnnotationConfiguration();
    }

    protected boolean isValidFragmentConfiguration() {
        return useWebFragmentsDetect;
    }

    protected FragmentConfiguration createFragmentConfiguration() {
        return new FragmentConfiguration();
    }

    protected EnvConfiguration createEnvConfiguration() {
        return new EnvConfiguration();
    }

    protected JettyWebXmlConfiguration createJettyWebXmlConfiguration() {
        return new JettyWebXmlConfiguration();
    }

    // -----------------------------------------------------
    //                                                 Await
    //                                                 -----
    public void await() { // public as parts
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
        info("...Registering the shutdown hook for the Jetty: lastModified=" + exp);
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
        info("...Shuting down the Jetty forcedly: port=" + port);
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
    //                                                                             Logging
    //                                                                             =======
    protected void info(String msg) {
        System.out.println(msg); // console as default not to depends specific logger
    }

    // ===================================================================================
    //                                                                            Accessor
    //                                                                            ========
    public Server getServer() {
        return server;
    }
}