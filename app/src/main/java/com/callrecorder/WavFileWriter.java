package com.callrecorder;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Helper that wraps a WAV header around raw PCM audio data.
 */
public class WavFileWriter implements Closeable {

    private final File file;
    private final FileOutputStream outputStream;
    private final int sampleRate;
    private final int channelCount;
    private final int bitsPerSample;
    private long totalBytesWritten = 0;

    public WavFileWriter(File file, int sampleRate, int channelCount, int bitsPerSample) throws IOException {
        this.file = file;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.bitsPerSample = bitsPerSample;

        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        outputStream = new FileOutputStream(file);
        writeHeader(0);
    }

    public void write(byte[] buffer, int length) throws IOException {
        outputStream.write(buffer, 0, length);
        totalBytesWritten += length;
    }

    @Override
    public void close() throws IOException {
        try {
            outputStream.flush();
            outputStream.close();
        } finally {
            finalizeHeader();
        }
    }

    private void writeHeader(long totalAudioLen) throws IOException {
        long totalDataLen = totalAudioLen + 36;
        long byteRate = sampleRate * channelCount * bitsPerSample / 8;
        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeInt(header, 4, (int) totalDataLen);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeInt(header, 16, 16);
        writeShort(header, 20, (short) 1);
        writeShort(header, 22, (short) channelCount);
        writeInt(header, 24, sampleRate);
        writeInt(header, 28, (int) byteRate);
        writeShort(header, 32, (short) (channelCount * bitsPerSample / 8));
        writeShort(header, 34, (short) bitsPerSample);
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeInt(header, 40, (int) totalAudioLen);

        outputStream.write(header, 0, 44);
    }

    private void finalizeHeader() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            long totalAudioLen = totalBytesWritten;
            long totalDataLen = totalAudioLen + 36;
            long byteRate = sampleRate * channelCount * bitsPerSample / 8;

            raf.seek(4);
            raf.writeInt(Integer.reverseBytes((int) totalDataLen));
            raf.seek(40);
            raf.writeInt(Integer.reverseBytes((int) totalAudioLen));
            raf.seek(28);
            raf.writeInt(Integer.reverseBytes((int) byteRate));
            raf.seek(32);
            raf.writeShort(Short.reverseBytes((short) (channelCount * bitsPerSample / 8)));
            raf.seek(34);
            raf.writeShort(Short.reverseBytes((short) bitsPerSample));
        }
    }

    private void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
        data[offset + 2] = (byte) ((value >> 16) & 0xff);
        data[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private void writeShort(byte[] data, int offset, short value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
    }

    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }
}
