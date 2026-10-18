// SPDX-License-Identifier: GPL-3.0-or-later
package app.pdscenes;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Audio files for kits: any recording the phone can play (MP3, OGG, FLAC, M4A...) decoded once into a 16-bit WAV
 * (dcs_voice~ reads WAV), and zips unpacked for import. (From the stem packs, retired Oct 18.)
 */
final class AudioFiles {

    private AudioFiles() { }

    private static final long DECODE_LIMIT_S = 60;

    /** Decodes any audio file the phone can play into a 16-bit PCM WAV (giving up after DECODE_LIMIT_S). */
    static void decode(File in, File out) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try (FileOutputStream os = new FileOutputStream(out)) {
            ex.setDataSource(in.getPath());
            int track = -1;
            MediaFormat fmt = null;
            for (int t = 0; t < ex.getTrackCount(); t++) {
                MediaFormat f = ex.getTrackFormat(t);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = t;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) {
                throw new IOException(in.getName() + " has no audio the phone can read");
            }
            ex.selectTrack(track);
            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            os.write(new byte[44]);   // the header, filled in at the end
            long dataBytes = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            long started = System.nanoTime();
            while (!outputDone) {
                // a broken file can leave a decoder waiting forever: give up after DECODE_LIMIT_S
                if (System.nanoTime() - started > DECODE_LIMIT_S * 1_000_000_000L) {
                    throw new IOException("decoding took longer than " + DECODE_LIMIT_S + " seconds, the file may be damaged");
                }
                if (!inputDone) {
                    int ib = codec.dequeueInputBuffer(10000);
                    if (ib >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(ib);
                        int n = buf == null ? -1 : ex.readSampleData(buf, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(ib, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int ob = codec.dequeueOutputBuffer(info, 10000);
                if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (ob >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(ob);
                    if (buf != null && info.size > 0) {
                        byte[] chunk = new byte[info.size];
                        buf.position(info.offset);
                        buf.get(chunk);
                        os.write(chunk);   // (16-bit PCM, the decoders' default output)
                        dataBytes += chunk.length;
                    }
                    codec.releaseOutputBuffer(ob, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
            os.getChannel().position(0);
            os.write(wavHeader(rate, channels, dataBytes));
        } catch (IOException | RuntimeException e) {
            out.delete();
            throw new IOException("Could not decode " + in.getName() + ": " + e.getMessage(), e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (RuntimeException ignored) {
                    // already stopped
                }
                codec.release();
            }
            ex.release();
        }
    }

    static byte[] wavHeader(int rate, int channels, long dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int) (36 + dataBytes));
        b.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) channels);
        b.putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int) dataBytes);
        return b.array();
    }

    /** Unpacks a zip into a fresh folder in {@code packsDir} (the caller installs, then deletes it). */
    static File unzipToStaging(InputStream zip, File packsDir) throws IOException {
        if (!packsDir.isDirectory() && !packsDir.mkdirs()) {
            throw new IOException("Cannot create " + packsDir);
        }
        File staging = SceneLibrary.newStaging(packsDir);
        SceneLibrary.unzip(zip, staging);
        return staging;
    }
}
