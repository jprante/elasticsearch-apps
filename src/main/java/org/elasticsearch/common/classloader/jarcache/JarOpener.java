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
import java.util.jar.JarFile;

/**
 * Abstraction of JAR opener which allows to implement various caching
 * policies. The opener receives URL pointing to the JAR file, along
 * with other meta-information, as a JarURLConnection instance. Then it has
 * to download the file (if it is remote) and open it.
 *
 * @version $Rev$ $Date$
 */
public interface JarOpener {
    /**
     * Given the URL connection (not yet connected), return JarFile
     * representing the resource. This method is invoked as a part of
     * the connect method in JarURLConnection.
     *
     * @param connection the connection for which the JAR file is required
     * @return opened JAR file
     * @throws IOException if I/O error occurs
     */
    public JarFile openJarFile(java.net.JarURLConnection connection, JarUrlStreamHandler jarUrlStreamHandler) throws IOException;
}
