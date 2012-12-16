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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * @version $Rev$ $Date$
 */
public class CachingJarOpener implements JarOpener {
    private final JarCache jarCache;

    public CachingJarOpener(JarCache jarCache) {
        this.jarCache = jarCache;
    }

    public JarFile openJarFile(java.net.JarURLConnection jarUrlConnection, JarUrlStreamHandler jarUrlStreamHandler) throws IOException {
        URL baseUrl = getBaseUrl(jarUrlConnection, jarUrlStreamHandler);
        return jarCache.getJarFile(baseUrl, jarUrlConnection);
    }

    public static URL getBaseUrl(java.net.JarURLConnection jarUrlConnection, JarUrlStreamHandler jarUrlStreamHandler) throws MalformedURLException {
        URL url = jarUrlConnection.getURL();
        String baseText = url.getPath();

        int bangLoc = baseText.lastIndexOf("!");
        String baseResourceText = baseText.substring(0, bangLoc);

        // if this is a nested jar entry
        if (baseResourceText.indexOf("!") >= 0) {
            URL baseResource = new URL("jar", null, -1, baseResourceText, jarUrlStreamHandler);
            return baseResource;
        }

        // normal non-nested jar entry
        return jarUrlConnection.getJarFileURL();
    }
}
