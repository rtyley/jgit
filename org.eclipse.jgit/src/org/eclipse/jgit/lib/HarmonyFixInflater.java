package org.eclipse.jgit.lib;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Created by IntelliJ IDEA.
 * User: roberto
 * Date: 19-Aug-2010
 * Time: 15:57:23
 * To change this template use File | Settings | File Templates.
 */
public class HarmonyFixInflater extends Inflater {

    private static final byte[] oneByteArray = new byte[1];

    public HarmonyFixInflater() {
        super(false);
    }

    public void setInput(byte[] b, int off, int len) {
        super.setInput(b, off, len);
    }

    public void setInput(byte[] b) {
        super.setInput(b);
    }

    public void setDictionary(byte[] b, int off, int len) {
        super.setDictionary(b, off, len);
    }

    public void setDictionary(byte[] b) {
        super.setDictionary(b);
    }

    public int getRemaining() {
        return super.getRemaining();
    }

    public boolean needsInput() {
        return super.needsInput();
    }

    public boolean needsDictionary() {
        return super.needsDictionary();
    }

    public boolean finished() {
        return super.finished();
    }

    public int inflate(byte[] b, int off, int len) throws DataFormatException {
        //return super.inflate(b, off, len);
        if (len!=0) {
            return super.inflate(b, off, len);
        }
        System.out.println("Performing zero-len harmony hack");

        int bytesInflated=super.inflate(oneByteArray, 0, 1); // have to pretend to want at least one byte so that the finished flag is correctly set
        if (bytesInflated>0) {
            throw new RuntimeException("The Harmony-Fix hack has served you ill, we were not supposed to read any data...");
        }
        return 0;
    }

    public int inflate(byte[] b) throws DataFormatException {
        return super.inflate(b);
    }

    public int getAdler() {
        return super.getAdler();
    }

    public int getTotalIn() {
        return super.getTotalIn();
    }

    public long getBytesRead() {
        return super.getBytesRead();
    }

    public int getTotalOut() {
        return super.getTotalOut();
    }

    public long getBytesWritten() {
        return super.getBytesWritten();
    }

    public void reset() {
        super.reset();
    }

    public void end() {
        super.end();
    }
}
