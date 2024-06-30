/*
 * Copyright 2015-2024 the original author or authors.
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dbflute.jetty.util.BoJtResourceUtil;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
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
 * @author cabos
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
    protected Predicate<String> webFragmentsSelector;

    // -----------------------------------------------------
    //                                              Stateful
    //                                              --------
    protected Server server;

    // ===================================================================================
    //                                                                         Constructor
    //                                                                         ===========
    /**
     * Create with port number and context path.
     * <pre>
     * e.g. has context path
     *  JettyBoot boot = new JettyBoot(8151, "/fortress");
     * 
     * e.g. no context path
     *  JettyBoot boot = new JettyBoot(8151, "");
     * </pre>
     * @param port The port number for the jetty server.
     * @param contextPath The context path for the jetty server, basically has slash prefix. (NotNull, EmptyAllowed)
     */
    public JettyBoot(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
    }

    // -----------------------------------------------------
    //                                                Option
    //                                                ------
    /**
     * Does it boot the jetty server as development mode?
     * @return this. (NotNull)
     */
    public JettyBoot asDevelopment() {
        development = true;
        return this;
    }

    /**
     * Does it boot the jetty server as development mode?
     * @param development Is it development mode?
     * @return this. (NotNull)
     */
    public JettyBoot asDevelopment(boolean development) {
        this.development = development;
        return this;
    }

    /**
     * Browse on desktop automatically after boot finished.
     * @return this. (NotNull)
     */
    public JettyBoot browseOnDesktop() {
        // wants to use this in production (e.g. DBFlute Intro) 
        //assertDevelopmentState();
        browseOnDesktop = true;
        return this;
    }

    /**
     * Suppress shutdown hook. (for development only)
     * @return this. (NotNull)
     */
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

    /**
     * You can detect embedded webroot.
     * @return this. (NotNull)
     */
    public JettyBoot useEmbeddedWebroot() {
        useEmbeddedWebroot = true;
        return this;
    }

    /**
     * You can detect annotations in all jar files.
     * @return this. (NotNull)
     */
    public JettyBoot useAnnotationDetect() {
        useAnnotationDetect = true;
        return this;
    }

    /**
     * You can detect 'META-INF' resources in jar files detected as web fragments. <br>
     * <span style="color: #CC4747; font-size: 120%">So you also needs to enable web fragments detect.</span>
     * @return this. (NotNull)
     */
    public JettyBoot useMetaInfoResourceDetect() {
        useMetaInfoResourceDetect = true;
        return this;
    }

    /**
     * You can detect '.tdl' files in all jar files.
     * @return this. (NotNull)
     */
    public JettyBoot useTldDetect() {
        useTldDetect = true;
        return this;
    }

    /**
     * You can detect web fragments in all jar files.
     * @return this. (NotNull)
     */
    public JettyBoot useWebFragmentsDetect() {
        useWebFragmentsDetect = true;
        return this;
    }

    /**
     * You can detect web fragments in selected jar files.
     * <pre>
     * boot.useMetaInfoResourceDetect().useWebFragmentsDetect(jarName -&gt; { // for swagger
     *     return jarName.contains("swagger-ui");
     * });
     * </pre>
     * @param oneArgLambda The callback for selector of web fragments, argument is jar name. (NotNull)
     * @return this. (NotNull)
     */
    public JettyBoot useWebFragmentsDetect(Predicate<String> oneArgLambda) { // you can select
        useWebFragmentsDetect = true;
        webFragmentsSelector = oneArgLambda;
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
        loggingBootSuccessful(uri);
        browseOnDesktopIfNeeds(uri);
        return this;
    }

    protected void prepareServer() {
        final WebAppContext context = prepareWebAppContext();
        final String serverHost = getServerHost();
        if (serverHost != null) {
            server = new Server(new InetSocketAddress(serverHost, port));
        } else { // means network connector binds to all network interfaces.
            server = new Server(port); // all requests are accepted regardless server host
        }
        server.setHandler(context);
    }

    protected String getServerHost() { // may be overridden
        return null; // as default, all acceptable
    }

    protected URI startServer() {
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("server start failed.", e);
        }
        return server.getURI();
    }

    protected void loggingBootSuccessful(URI uri) {
        info(buildBootSuccessfulLogMessage(uri));
    }

    protected String buildBootSuccessfulLogMessage(URI uri) {
        final String serverHost = getServerHost();
        if (serverHost != null) {
            return doBuildBootSuccessfulLogMessage(uri.toString());
        } else { // means all hosts in URL are acceptable
            final String uriHost = uri.getHost(); // may be your Wifi's IP address
            final String loggingUri = uri.toString().replace(uriHost, "localhost"); // so switch as default
            return doBuildBootSuccessfulLogMessage(loggingUri);
        }
    }

    protected String doBuildBootSuccessfulLogMessage(String uri) {
        return "Boot successful" + (development ? " as development" : "") + ": url -> " + uri;
    }

    protected WebAppContext prepareWebAppContext() {
        final URL warLocation = getWarLocation();
        final String path;
        try {
            path = warLocation.toURI().getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to get the war URI: " + warLocation, e);
        }
        final WebAppContext context = new WebAppContext();
        if (path != null && isWarableFile(path)) {
            context.setWar(warLocation.toExternalForm());
        } else {
            context.setResourceBase(getResourceBase());
        }
        context.setConfigurations(prepareConfigurations());
        context.setContextPath(contextPath);
        setupClasspathJarResourceIfNeeds(context); // basically for local development (and e.g. swagger) 
        return context;
    }

    // -----------------------------------------------------
    //                                          War Handling
    //                                          ------------
    protected URL getWarLocation() {
        // e.g.
        // /.../maihama-dockside.war in production (executable jar)
        // /.../dbflute-intro.jar in production (executable jar)
        // /.../jetty-boot-x.x.x.jar in local development by library reference
        // /.../jetty-boot/target/classes/ in local development by project reference
        return JettyBoot.class.getProtectionDomain().getCodeSource().getLocation();
    }

    protected boolean isWarableFile(String path) {
        if (path.endsWith(".war")) { // e.g. /.../maihama-dockside.war
            return true;
        }
        // is it war-able jar file? (e.g. DBFlute Intro)
        // pureName is e.g.
        //  o dbflute-intro.jar in production (executable jar)
        //  o jetty-boot-x.x.x.jar in local development by library reference (cannot be war-able)
        //  o (empty string) in local development by project reference (cannot be war-able)
        final String delimiter = "/";
        final String pureName;
        if (path.contains(delimiter)) {
            pureName = path.substring(path.lastIndexOf(delimiter) + delimiter.length());
        } else {
            pureName = path;
        }
        return !pureName.startsWith("jetty-boot") && pureName.endsWith(".jar");
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
        // meta-info resource detect is triggered by web-fragment detect (to be same specification as tomcat) 
        //return useMetaInfoResourceDetect || useTldDetect || useWebFragmentsDetect;
        return useTldDetect || useWebFragmentsDetect;
    }

    protected MetaInfConfiguration createMetaInfConfiguration() {
        return new SelectableMetaInfConfiguration(useMetaInfoResourceDetect, useWebFragmentsDetect, useTldDetect, webFragmentsSelector);
    }

    public static class SelectableMetaInfConfiguration extends MetaInfConfiguration {

        protected final boolean useMetaInfoResourceDetect;
        protected final boolean useWebFragmentsDetect;
        protected final boolean useTldDetect;
        protected final Predicate<String> webFragmentsSelector;

        public SelectableMetaInfConfiguration(boolean useMetaInfoResourceDetect, boolean useWebFragmentsDetect, boolean useTldDetect,
                Predicate<String> webFragmentsSelector) {
            this.useMetaInfoResourceDetect = useMetaInfoResourceDetect;
            this.useWebFragmentsDetect = useWebFragmentsDetect;
            this.useTldDetect = useTldDetect;
            this.webFragmentsSelector = webFragmentsSelector;
        }

        @Override
        public void scanForResources(WebAppContext context, Resource target, ConcurrentHashMap<Resource, Resource> cache) throws Exception {
            if (useMetaInfoResourceDetect) {
                if (isTargetWebFragment(target)) { // depends on web-fragment detect (to be same specification as tomcat)
                    super.scanForResources(context, target, cache);
                }
            }
        }

        @Override
        public void scanForFragment(WebAppContext context, Resource jar, ConcurrentHashMap<Resource, Resource> cache) throws Exception {
            if (useWebFragmentsDetect && isTargetWebFragment(jar)) {
                super.scanForFragment(context, jar, cache);
            }
        }

        protected boolean isTargetWebFragment(Resource jar) {
            return webFragmentsSelector != null && webFragmentsSelector.test(jar.getName());
        }

        @Override
        public void scanForTlds(WebAppContext context, Resource jar, ConcurrentHashMap<Resource, Collection<URL>> cache) throws Exception {
            if (useTldDetect) {
                super.scanForTlds(context, jar, cache);
            }
        }
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
    //                                         Classpath Jar
    //                                         -------------
    // cannot use web-fragment and meta-inf as default
    // because jetty does not see classpath jar resources
    // so manually enable it
    protected void setupClasspathJarResourceIfNeeds(WebAppContext context) {
        if (isWarableWorld() || !isValidMetaInfConfiguration()) {
            return;
        }
        // may be local development and uses meta-inf configuration here
        final List<String> classpathList = extractJarClassspathList();
        for (String classpath : classpathList) {
            final String jarPath = convertClasspathToJarPath(classpath);
            final URL url;
            try {
                url = new URL(jarPath);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Failed to create URL from the jar path: " + jarPath, e);
            }
            context.getMetaData().addContainerResource(new JarResource(url) {
            });
        }
    }

    protected boolean isWarableWorld() {
        final URL warLocation = getWarLocation();
        final String path;
        try {
            path = warLocation.toURI().getPath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to get the war URI: " + warLocation, e);
        }
        return path != null && isWarableFile(path);
    }

    protected List<String> extractJarClassspathList() {
        final String classpathExp = System.getProperty("java.class.path");
        if (classpathExp == null) {
            return Collections.emptyList();
        }
        return Stream.of(classpathExp.split(":")).filter(classpath -> {
            if (!classpath.endsWith(".jar")) {
                return false;
            }
            // not perfect allowed, remove them as possible
            if (classpath.startsWith("/Library/Java/") || classpath.startsWith("/System/Library/Java")) { // for MacOSX
                return false;
            }
            final int lastSlashIndex = classpath.lastIndexOf("/");
            if (lastSlashIndex >= 0) { // basically here
                final String pathOnly = classpath.substring(0, lastSlashIndex); // without file name
                if (pathOnly.endsWith("/jre/lib") || pathOnly.endsWith("/jre/lib/ext")) { // for Windows (also MacOSX)
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
    }

    protected String convertClasspathToJarPath(String classpath) {
        return "jar:file:" + classpath + "!/";
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
    protected void browseOnDesktopIfNeeds(final URI uri) {
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