package org.elasticsearch.apps;

import java.util.Comparator;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public class DependencyInfo {

    private final MavenResolvedArtifact artifact;
    private final int level;

    DependencyInfo(MavenResolvedArtifact artifact, int level) {
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
