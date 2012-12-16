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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Alternative implementation of URLStreamHandler for JAR files which
 * supports a customizable policies for opening the JarFile. This policy can
 * be used to implement support for nested jars and caching.
 *
 * @version $Rev$ $Date$
 */
public class JarUrlStreamHandler extends URLStreamHandler {
    private final JarOpener opener;

    /**
     * Create new JarUrlStreamHandler that will use its separate URL cache
     * managed by a newly created {@link CachingJarOpener} instance.
     */
    public JarUrlStreamHandler() {
        this(new CachingJarOpener(new TempJarCache()));
    }

    /**
     * Create new JarUrlStreamHandler that will use specified
     * JarOpener.
     *
     * @param opener JAR opener that handles file download and caching
     */
    public JarUrlStreamHandler(JarOpener opener) {
        this.opener = opener;
    }

    public JarOpener getOpener() {
        return opener;
    }

    public URLConnection openConnection(URL url) throws IOException {
        try {
            String urlPath = url.getPath();
            if (urlPath.startsWith("jar:")) {
                urlPath = urlPath.substring("jar:".length());
                setURL(url, "jar", "", -1, null, null, urlPath, null, null);
            }
            return new JarUrlConnection(url, this);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    protected void parseURL(URL url, String spec, int start, int limit) {
        String specPath = spec.substring(start, limit);

        String urlPath = null;

        if (specPath.charAt(0) == '/') {
            urlPath = specPath;
        } else if (specPath.charAt(0) == '!') {
            String relPath = url.getFile();

            int bangLoc = relPath.lastIndexOf("!");

            if (bangLoc < 0) {
                urlPath = relPath + specPath;
            } else {
                urlPath = relPath.substring(0,
                        bangLoc) + specPath;
            }
        } else {
            String relPath = url.getFile();

            if (relPath != null) {
                int lastSlashLoc = relPath.lastIndexOf("/");

                if (lastSlashLoc < 0) {
                    urlPath = "/" + specPath;
                } else {
                    urlPath = relPath.substring(0, lastSlashLoc + 1) + specPath;
                }
            } else {
                urlPath = specPath;
            }
        }

        if (urlPath.startsWith("jar:")) {
            urlPath = urlPath.substring("jar:".length());
        }
        setURL(url, "jar", "", -1, null, null, urlPath, null, null);
    }
}
