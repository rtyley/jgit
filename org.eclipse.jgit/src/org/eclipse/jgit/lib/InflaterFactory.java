package org.eclipse.jgit.lib;

import java.util.zip.Inflater;

public interface InflaterFactory {

    public static final InflaterFactory STANDARD_FACTORY = new InflaterFactory() {
        public Inflater create() { return new Inflater(); }

        public void decommision(Inflater inflater) { inflater.end(); }
    };

    Inflater create();

    void decommision(Inflater inflater);

}
