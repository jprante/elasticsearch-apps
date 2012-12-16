/**
 *
 * Copyright 2005 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.elasticsearch.common.classloader.jarcache;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Permission;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Alternative implementation of {@link java.net.JarURLConnection} which
 * supports a customizable policies for opening the JarFile. This policy can
 * be used to implement support for nested jars and caching.
 *
 * @version $Rev$ $Date$
 */
public class JarUrlConnection extends java.net.JarURLConnection {
    private final JarUrlStreamHandler jarUrlStreamHandler;
    private boolean connected;
    private JarFile jarFile;
    private JarEntry jarEntry;
    private String entryName;

    /**
     * Creates JarUrlConnection for a given URL, using specified JAR opener.
     *
     * @param url the URL to open connection to
     */
    public JarUrlConnection(URL url, JarUrlStreamHandler jarUrlStreamHandler) throws IOException {
        super(url);
        this.jarUrlStreamHandler = jarUrlStreamHandler;
    }

    public synchronized void connect() throws IOException {
        if (connected) return;

        JarOpener opener = jarUrlStreamHandler.getOpener();
        jarFile = opener.openJarFile(this, jarUrlStreamHandler);
        if (jarFile != null) {
            URL url = getURL();
            String baseText = url.getPath();

            int bangLoc = baseText.lastIndexOf("!");
            if (bangLoc <= (baseText.length() - 2) && baseText.charAt(bangLoc + 1) == '/') {
                if (bangLoc + 2 == baseText.length()) {
                    entryName = null;
                } else {
                    entryName = baseText.substring(bangLoc + 2);
                }
            } else {
                throw new MalformedURLException("No !/ in url: " + url.toExternalForm());
            }

            if (entryName != null) {
                jarEntry = jarFile.getJarEntry(entryName);
                if (jarEntry == null) {
                    throw new FileNotFoundException("Entry " + entryName + " not found in " + jarFile.getName());
                }
            }
        }
        connected = true;
    }

    public synchronized String getEntryName() {
        return entryName;
    }

    public synchronized JarFile getJarFile() throws IOException {
        connect();
        return jarFile;
    }

    public synchronized JarEntry getJarEntry() throws IOException {
        connect();
        return jarEntry;
    }

    public synchronized InputStream getInputStream() throws IOException {
        connect();
        return jarFile.getInputStream(jarEntry);
    }

    public Permission getPermission() throws IOException {
        return getJarFileURL().openConnection().getPermission();
    }
}
