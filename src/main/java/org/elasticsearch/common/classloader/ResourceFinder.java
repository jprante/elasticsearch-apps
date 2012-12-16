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

import java.net.URL;
import java.util.Enumeration;

/**
 * Abstraction of resource searching policy. Given resource name, the resource
 * finder performs implementation-specific lookup, and, if it is able to locate
 * the resource, returns the {@link ResourceHandle handle(s)} or URL(s) of it.
 *
 */
public interface ResourceFinder {

    /**
     * Find the resource by name and return URL of it if found.
     *
     * @param name the resource name
     * @return resource URL or null if resource was not found
     */
    public URL findResource(String name);

    /**
     * Find all resources with given name and return enumeration of their URLs.
     *
     * @param name the resource name
     * @return enumeration of resource URLs (possibly empty).
     */
    public Enumeration findResources(String name);

    /**
     * Get the resource by name and, if found, open connection to it and return
     * the {@link ResourceHandle handle} of it.
     *
     * @param name the resource name
     * @return resource handle or null if resource was not found
     */
    public ResourceHandle getResource(String name);

    /**
     * Get all resources with given name and return enumeration of their
     * {@link ResourceHandle resource handles}.
     *
     * @param name the resource name
     * @return enumeration of resource handles (possibly empty).
     */
    public Enumeration getResources(String name);
}
