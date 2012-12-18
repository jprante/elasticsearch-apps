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

package org.elasticsearch.apps.support;

import java.util.Comparator;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public class DependencyInfo {

    private final MavenResolvedArtifact artifact;
    private final int level;

    public DependencyInfo(MavenResolvedArtifact artifact, int level) {
        this.artifact = artifact;
        this.level = level;
    }

    public MavenResolvedArtifact getArtifact() {
        return artifact;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object t) {
        return t instanceof DependencyInfo ? artifact.equals(((DependencyInfo) t).getArtifact()) : false;
    }

    @Override
    public int hashCode() {
        return artifact.hashCode();
    }

    public static Comparator<DependencyInfo> comparator() {
        return comparator;
    }
    private final static DependencyComparator comparator = new DependencyComparator();

    private static class DependencyComparator implements Comparator<DependencyInfo> {

        @Override
        public int compare(DependencyInfo t1, DependencyInfo t2) {
            int d = t1.getLevel() - t2.getLevel();
            if (d != 0) {
                return d;
            }
            return t1.getArtifact().getCoordinate().toCanonicalForm().compareTo(t2.getArtifact().getCoordinate().toCanonicalForm());

        }
    }
}
