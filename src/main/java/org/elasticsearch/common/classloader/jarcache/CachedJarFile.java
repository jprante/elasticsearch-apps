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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permission;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * @version $Rev$ $Date$
 */
public class CachedJarFile extends JarFile {
    private static File DUMMY_JAR_FILE;

    private static synchronized File getDummyJarFile() {
        if (DUMMY_JAR_FILE == null || !DUMMY_JAR_FILE.isFile() || !DUMMY_JAR_FILE.canRead()) {
            try {
                DUMMY_JAR_FILE = File.createTempFile("geronimo-dummy", ".jar");
                DUMMY_JAR_FILE.deleteOnExit();
                new JarOutputStream(new FileOutputStream(DUMMY_JAR_FILE), new Manifest()).close();
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        return DUMMY_JAR_FILE;
    }

    private final JarFile actualJarFile;
    private final Permission permission;

    public CachedJarFile(JarFile jarFile, Permission permission) throws IOException {
        super(getDummyJarFile());
        this.actualJarFile = jarFile;
        this.permission = permission;
    }

    public Manifest getManifest() throws IOException {
        Manifest original = actualJarFile.getManifest();
        if (original == null) {
            return null;
        }

        // make sure the original manifest is not modified
        Manifest copy = new Manifest();
        copy.getMainAttributes().putAll(original.getMainAttributes());
        for (Map.Entry<String,Attributes> entry : original.getEntries().entrySet()) {
            copy.getEntries().put(entry.getKey(), new Attributes(entry.getValue()));
        }
        return copy;
    }

    public Permission getPermission() {
        return permission;
    }

    public JarFile getActualJarFile() {
        return actualJarFile;
    }

    public String getName() {
        return actualJarFile.getName();
    }

    public int size() {
        return actualJarFile.size();
    }

    public JarEntry getJarEntry(String name) {
        return actualJarFile.getJarEntry(name);
    }

    public ZipEntry getEntry(String name) {
        return actualJarFile.getEntry(name);
    }

    public Enumeration entries() {
        return actualJarFile.entries();
    }

    public InputStream getInputStream(ZipEntry ze) throws IOException {
        return actualJarFile.getInputStream(ze);
    }

    public void close() throws IOException {
        // no op; do NOT close file while still in cache
    }
}
