/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.apps;

import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.Plugin;

/**
 * An App is an extension point for Elasticsearch allowing to plug in custom functionality,
 * extending the basic Plugin class with grouping and versioning capabilities useful
 * for dependency resolving.
 * 
 * An App can be dynamically injected with {@link Module} by implementing <tt>onModule(AnyModule)</tt> method
 * removing the need to override {@link #processModule(org.elasticsearch.common.inject.Module)} and check using
 * instanceof.
 */
public interface App extends Plugin {

    /**
     * The group of this App
     * @return the group identifier
     */
     String groupId();
     
     /**
      * The artifact name of this App
      * @return the artifact nae
      */
     String artifactId();
     
     /**
      * The version of this App
      * @return the version
      */
     String version();
     
     /**
      * The classifier of this App
      * @return the classifier
      */
     String classifier();
     
     /**
      * The type of this App
      * @return the type
      */
     String type();
     
     /**
      * The canonical form of the name of this App
      * @return the cononical form
      */
     String getCanonicalForm();
}
