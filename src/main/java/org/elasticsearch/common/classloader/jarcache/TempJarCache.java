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
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @version $Rev$ $Date$
 */
public class TempJarCache implements JarCache {
    private final LinkedHashMap cache = new LinkedHashMap();
    private final ReferenceQueue referenceQueue = new ReferenceQueue();
    private final File cacheDir;

    public TempJarCache() {
        String tmpdir = System.getProperty("org.apache.geronimo.tmpdir", System.getProperty("java.io.tmpdir"));
        this.cacheDir = new File(new File(tmpdir, "geronimo"), "urlcache");
        verifyCacheDirectory();
    }

    public TempJarCache(File cacheDir) {
        this.cacheDir = cacheDir;
        verifyCacheDirectory();
    }

    protected void verifyCacheDirectory() {
        cacheDir.mkdirs();
        if (!cacheDir.exists()) {
            throw new IllegalArgumentException("Could not create jar cache directory: " + cacheDir.getAbsolutePath());
        }
        if (!cacheDir.isDirectory()) {
            throw new IllegalArgumentException("Cache directory is not a directory: " + cacheDir.getAbsolutePath());
        }
    }

    public void destroy() {
        Map cache;
        synchronized (this.cache) {
            cache = new HashMap(this.cache);
            this.cache.clear();
        }

        for (Iterator i = cache.values().iterator(); i.hasNext();) {
            WeakReference weakReference = (WeakReference) i.next();
            weakReference.clear();
        }
    }

    public JarFile getJarFile(URL baseUrl) throws IOException {
        return getJarFile(baseUrl, null);
    }

    public JarFile getJarFile(URL baseUrl, URLConnection urlConnectionProperties) throws IOException {
        // first you get to do some cleaning
        reapFiles();

        // first check the cache
        CachedJarFile cachedJarFile = null;
        synchronized (cache) {
            WeakReference reference = (WeakReference) cache.get(baseUrl);
            if (reference != null) {
                cachedJarFile = (CachedJarFile) reference.get();
            }
        }
        if (cachedJarFile != null) {
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkPermission(cachedJarFile.getPermission());
            }
            return cachedJarFile;
        }

        // check if the baseUrl is a jar on the local file system
        try {
            URI uri = new URI(baseUrl.toString());
            if (isLocalFile(uri)) {
                File file = new File(uri);
                Permission perm = new FilePermission(file.getAbsolutePath(), "read");
                cachedJarFile = new CachedJarFile(new JarFile(file), perm);
            }
        }
        catch (URISyntaxException e) {
            // apparently not a local file
        }

        // if not a local jar, download to the cache
        if (cachedJarFile == null) {
            URLConnection urlConnection = baseUrl.openConnection();

            if (urlConnectionProperties != null) {
                // set up the properties based on the JarURLConnection
                urlConnection.setAllowUserInteraction(urlConnectionProperties.getAllowUserInteraction());
                urlConnection.setDoInput(urlConnectionProperties.getDoInput());
                urlConnection.setDoOutput(urlConnectionProperties.getDoOutput());
                urlConnection.setIfModifiedSince(urlConnectionProperties.getIfModifiedSince());

                Map map = urlConnectionProperties.getRequestProperties();
                for (Iterator itr = map.entrySet().iterator(); itr.hasNext();) {
                    Map.Entry entry = (Map.Entry) itr.next();
                    urlConnection.setRequestProperty((String) entry.getKey(), (String) entry.getValue());
                }

                urlConnection.setUseCaches(urlConnectionProperties.getUseCaches());
            }

            cachedJarFile = cacheFile(urlConnection);
        }

        // if no input came (e.g. due to NOT_MODIFIED), do not cache
        if (cachedJarFile == null) return null;

        // optimistic locking
        synchronized (cache) {
            WeakReference reference = (WeakReference) cache.get(baseUrl);
            if (reference != null) {
                CachedJarFile asyncResult = (CachedJarFile) reference.get();
                if (asyncResult != null) {
                    // some other thread already retrieved the file; return w/o
                    // security check since we already succeeded in getting past it
                    cachedJarFile.getActualJarFile().close();
                    return asyncResult;
                }
            }
            cache.put(baseUrl, new WeakReference(cachedJarFile, referenceQueue));
            return cachedJarFile;
        }
    }

    public void reapFiles() {
        for (Reference reference = referenceQueue.poll(); reference != null; reference = referenceQueue.poll()) {
            reference.clear();
        }
    }

    private CachedJarFile cacheFile(final URLConnection urlConnection) throws IOException {
        CachedJarFile cachedJarFile = null;
        final InputStream in = urlConnection.getInputStream();

        try {
            cachedJarFile = (CachedJarFile) AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IOException {
                    File file = File.createTempFile("jar_cache", "", cacheDir);
                    FileOutputStream out = null;
                    try {
                        out = new FileOutputStream(file);
                        writeAll(in, out);
                        safeClose(out);
                        out = null;

                        JarFile jarFile = new JarFile(file, true, JarFile.OPEN_READ | JarFile.OPEN_DELETE);
                        return new CachedJarFile(jarFile, urlConnection.getPermission());
                    } catch (IOException e) {
                        safeClose(out);
                        file.delete();
                        throw e;
                    }
                }
            });
        }
        catch (PrivilegedActionException pae) {
            throw (IOException) pae.getException();
        } finally {
            safeClose(in);
        }
        return cachedJarFile;
    }

    public class CacheJarFileReference extends WeakReference {
        private final JarFile actualJarFile;

        public CacheJarFileReference(CachedJarFile cachedJarFile, ReferenceQueue q) {
            super(cachedJarFile, q);
            actualJarFile = cachedJarFile.getActualJarFile();
        }

        public void clear() {
            try {
                actualJarFile.close();
            } catch (IOException ignroed) {
            }
            super.clear();
        }

        public JarFile getActualJarFile() {
            return actualJarFile;
        }
    }

    private static void safeClose(OutputStream thing) {
        if (thing != null) {
            try {
                thing.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void safeClose(InputStream thing) {
        if (thing != null) {
            try {
                thing.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeAll(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) > 0) {
            out.write(buffer, 0, count);
        }
        out.flush();
    }

    private static boolean isLocalFile(URI uri) {
        if (!uri.isAbsolute()) {
            return false;
        }
        if (uri.isOpaque()) {
            return false;
        }
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getAuthority() != null) {
            return false;
        }
        if (uri.getFragment() != null) {
            return false;
        }
        if (uri.getQuery() != null) {
            return false;
        }
        return uri.getPath().length() > 0;
    }
}
