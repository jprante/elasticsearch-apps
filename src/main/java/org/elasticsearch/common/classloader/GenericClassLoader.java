/**
 *
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.elasticsearch.common.classloader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

/**
 * This class loader can be used to find class, resource and library
 * {@link ResourceHandle handles} as well as load classes, resources and
 * libraries using abstract {@link ResourceFinder} entity encapsulating the
 * searching approach. Resource handles allow accessing meta-information (like
 * Attributes, Certificates etc.) related to classes, resources and libraries
 * prior to loading them.
 * 
 * GenericClassLoader is intended to be used as a base for custom class loaders.
 * In most applications, GenericClassLoader can be used directly -- the
 * application-specific functionality of resource searching can often be
 * completely delegated to the resource finder. See {@link URIClassLoader} for a
 * concrete implementation using a simple resource finder.
 *
 */
public class GenericClassLoader extends SecureClassLoader {

    protected ResourceFinder finder;
    private AccessControlContext acc;

    /**
     * Creates new GenericClassLoader instance using specified
     * {@link ResourceFinder} to find resources and having specified parent
     * class loader.
     */
    public GenericClassLoader(ResourceFinder finder, ClassLoader parent) {
        super(parent);
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        this.finder = finder;
        acc = AccessController.getContext();
    }

    /**
     * Creates new GenericClassLoader instance using specified
     * {@link ResourceFinder} to find resources and with default parent class
     * loader.
     */
    public GenericClassLoader(ResourceFinder finder) {
        super();
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        this.finder = finder;
        //acc = AccessController.getContext();
    }

    /**
     * Finds and loads the class with the specified name.
     *
     * @param name the name of the class
     * @return the resulting class
     * @throws ClassNotFoundException if the class could not be found
     */
    protected Class findClass(final String name) throws ClassNotFoundException {
        try {
            return (Class) AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws ClassNotFoundException {
                    String path = name.replace('.', '/').concat(".class");
                    ResourceHandle h = finder.getResource(path);
                    if (h != null) {
                        try {
                            return defineClass(name, h);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    } else {
                        throw new ClassNotFoundException(name);
                    }
                }
            }, acc);
        } catch (java.security.PrivilegedActionException pae) {
            throw (ClassNotFoundException) pae.getException();
        }
    }

    protected Class defineClass(String name, ResourceHandle h) throws IOException {
        int i = name.lastIndexOf('.');
        URL url = h.getCodeSourceURL();
        if (i != -1) { // check package
            String pkgname = name.substring(0, i);
            // check if package already loaded
            Package pkg = getPackage(pkgname);
            Manifest man = h.getManifest();
            if (pkg != null) {
                // package found, so check package sealing
                boolean ok;
                if (pkg.isSealed()) {
                    // verify that code source URLs are the same
                    ok = pkg.isSealed(url);
                } else {
                    // make sure we are not attempting to seal the package
                    // at this code source URL
                    ok = (man == null) || !isSealed(pkgname, man);
                }
                if (!ok) {
                    throw new SecurityException("sealing violation: " + name);
                }
            } else {
                // package not yet defined
                if (man != null) {
                    definePackage(pkgname, man, url);
                } else {
                    definePackage(pkgname, null, null, null, null, null, null, null);
                }
            }
        }

        // now read the class bytes and define the class
        byte[] b = h.getBytes();
        java.security.cert.Certificate[] certs = h.getCertificates();
        CodeSource cs = new CodeSource(url, certs);
        return defineClass(name, b, 0, b.length, cs);
    }

    /**
     * returns true if the specified package name is sealed according to the
     * given manifest.
     */
    private boolean isSealed(String name, Manifest man) {
        String path = name.replace('.', '/').concat("/");
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    /**
     * Finds the resource with the specified name.
     *
     * @param name the name of the resource
     * @return a <code>URL</code> for the resource, or <code>null</code> if the
     * resource could not be found.
     */
    protected URL findResource(final String name) {
        return (URL) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return finder.findResource(name);
            }
        }, acc);
    }

    /**
     * Returns an Enumeration of URLs representing all of the resources having
     * the specified name.
     *
     * @param name the resource name
     * @return an <code>Enumeration</code> of <code>URL</code>s
     * @throws IOException if an I/O exception occurs
     */
    protected Enumeration findResources(final String name) throws IOException {
        return (Enumeration) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return finder.findResources(name);
            }
        }, acc);
    }

    /**
     * Returns the absolute path name of a native library. The VM invokes this
     * method to locate the native libraries that belong to classes loaded with
     * this class loader. If this method returns
     * <code>null</code>, the VM searches the library along the path specified
     * as the
     * <code>java.library.path</code> property. This method invoke
     * {@link #getLibraryHandle} method to find handle of this library. If the
     * handle is found and its URL protocol is "file", the system-dependent
     * absolute library file path is returned. Otherwise this method returns
     * null. <p>
     * <p/>
     * Subclasses can override this method to provide specific approaches in
     * library searching.
     *
     * @param libname the library name
     * @return the absolute path of the native library
     * @see System#loadLibrary(String)
     * @see System#mapLibraryName(String)
     */
    protected String findLibrary(String libname) {
        ResourceHandle md = getLibraryHandle(libname);
        if (md == null) {
            return null;
        }
        URL url = md.getURL();
        if (!"file".equals(url.getProtocol())) {
            return null;
        }
        return new File(URI.create(url.toString())).getPath();
    }
//
//    /**
//     * Gets the ResourceHandle object for the specified loaded class.
//     *
//     * @param clazz the Class
//     * @return the ResourceHandle of the Class
//     */
//    protected ResourceHandle getClassHandle(Class clazz)
//    {
//        return null;
//    }

    /**
     * Finds the ResourceHandle object for the class with the specified name.
     * Unlike
     * <code>findClass()</code>, this method does not load the class.
     *
     * @param name the name of the class
     * @return the ResourceHandle of the class
     */
    protected ResourceHandle getClassHandle(final String name) {
        String path = name.replace('.', '/').concat(".class");
        return getResourceHandle(path);
    }

    /**
     * Finds the ResourceHandle object for the resource with the specified name.
     *
     * @param name the name of the resource
     * @return the ResourceHandle of the resource
     */
    protected ResourceHandle getResourceHandle(final String name) {
        return (ResourceHandle) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return finder.getResource(name);
            }
        }, acc);
    }

    /**
     * Finds the ResourceHandle object for the native library with the specified
     * name. The library name must be '/'-separated path. The last part of this
     * path is substituted by its system-dependent mapping (using
     * {@link System#mapLibraryName(String)} method). Next, the
     * <code>ResourceFinder</code> is used to look for the library as it was
     * ordinary resource. <p>
     * <p/>
     * Subclasses can override this method to provide specific approaches in
     * library searching.
     *
     * @param name the name of the library
     * @return the ResourceHandle of the library
     */
    protected ResourceHandle getLibraryHandle(final String name) {
        int idx = name.lastIndexOf('/');
        String path;
        String simplename;
        if (idx == -1) {
            path = "";
            simplename = name;
        } else if (idx == name.length() - 1) { // name.endsWith('/')
            throw new IllegalArgumentException(name);
        } else {
            path = name.substring(0, idx + 1); // including '/'
            simplename = name.substring(idx + 1);
        }
        return getResourceHandle(path + System.mapLibraryName(simplename));
    }

    /**
     * Returns an Enumeration of ResourceHandle objects representing all of the
     * resources having the specified name.
     *
     * @param name the name of the resource
     * @return the ResourceHandle of the resource
     */
    protected Enumeration getResourceHandles(final String name) {
        return (Enumeration) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return finder.getResources(name);
            }
        }, acc);
    }

    /**
     * Defines a new package by name in this ClassLoader. The attributes
     * contained in the specified Manifest will be used to obtain package
     * version and sealing information. For sealed packages, the additional URL
     * specifies the code source URL from which the package was loaded.
     *
     * @param name the package name
     * @param man the Manifest containing package version and sealing
     * information
     * @param url the code source url for the package, or null if none
     * @return the newly defined Package object
     * @throws IllegalArgumentException if the package name duplicates an
     * existing package either in this class loader or one of its ancestors
     */
    protected Package definePackage(String name, Manifest man, URL url) throws IllegalArgumentException {
        String path = name.replace('.', '/').concat("/");
        URL sealBase = null;

        Attributes entryAttr = man.getAttributes(path);
        Attributes mainAttr = man.getMainAttributes();

        String specTitle = getManifestVal(entryAttr, mainAttr, Name.SPECIFICATION_TITLE);
        String specVersion = getManifestVal(entryAttr, mainAttr, Name.SPECIFICATION_VERSION);
        String specVendor = getManifestVal(entryAttr, mainAttr, Name.SPECIFICATION_VENDOR);
        String implTitle = getManifestVal(entryAttr, mainAttr, Name.IMPLEMENTATION_TITLE);
        String implVersion = getManifestVal(entryAttr, mainAttr, Name.IMPLEMENTATION_VERSION);
        String implVendor = getManifestVal(entryAttr, mainAttr, Name.IMPLEMENTATION_VENDOR);
        String sealed = getManifestVal(entryAttr, mainAttr, Name.SEALED);

        if ("true".equalsIgnoreCase(sealed)) {
            sealBase = url;
        }

        return definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }

    private static String getManifestVal(Attributes entryAttr, Attributes mainAttr, Name key) {
        String val = null;
        if (entryAttr != null) {
            val = entryAttr.getValue(key);
        }
        if (val == null && mainAttr != null) {
            val = mainAttr.getValue(key);
        }
        return val;
    }

    public static URLStreamHandler getDefaultURLStreamHandler(String protocol) {

//        String pkgList = (String) java.security.AccessController.doPrivileged(
//                new GetPropertyAction("java.protocol.handler.pkgs", ""));

        String pkgList = System.getProperty("java.protocol.handler.pkgs", "");

        if (pkgList.length() > 0) {
            pkgList += "|";
        }
        pkgList += "sun.net.www.protocol";

        StringTokenizer tokenizer = new StringTokenizer(pkgList, "|");

        while (tokenizer.hasMoreTokens()) {
            String pkg = tokenizer.nextToken().trim();
            try {
                String clname = pkg + "." + protocol + ".Handler";
                Class cls;
                try {
                    cls = Class.forName(clname);
                } catch (ClassNotFoundException e) {
                    cls = Class.forName(clname, false, null);
                }
                return (URLStreamHandler) cls.newInstance();
            } catch (Exception e) {
                // ignore and try next one
            }
        }
        return null;
    }
}
