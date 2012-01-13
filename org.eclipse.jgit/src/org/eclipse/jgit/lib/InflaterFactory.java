package org.eclipse.jgit.lib;

import java.util.zip.Inflater;

public interface InflaterFactory {

    public static final InflaterFactory STANDARD_FACTORY = new InflaterFactory() {
        public Inflater create() { return new Inflater(); }
    };

    Inflater create();

}
