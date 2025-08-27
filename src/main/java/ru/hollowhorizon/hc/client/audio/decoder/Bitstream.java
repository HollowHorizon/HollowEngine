package ru.hollowhorizon.hc.client.audio.decoder;

/*
 * 11/19/04 1.0 moved to LGPL.
 *
 * 11/17/04 Uncomplete frames discarded. E.B, javalayer@javazoom.net
 *
 * 12/05/03 ID3v2 tag returned. E.B, javalayer@javazoom.net
 *
 * 12/12/99 Based on Ibitstream. Exceptions thrown on errors, Temporary removed seek functionality. mdm@techie.com
 *
 * 02/12/99 : Java Conversion by E.B , javalayer@javazoom.net
 *
 * 04/14/97 : Added function prototypes for new syncing and seeking mechanisms. Also made this file portable. Changes made by Jeff
 * Tsay
 *
 * @(#) ibitstream.h 1.5, last edit: 6/15/94 16:55:34
 *
 * @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *
 * @(#) Berlin University of Technology----------------------------------------------------------------------- This program is
 * free software; you can redistribute it and/or modify it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * ----------------------------------------------------------------------
 */

import java.io.*;

public final class Bitstream {
    static byte INITIAL_SYNC = 0;
    static byte STRICT_SYNC = 1;
    private static final int BUFFER_INT_SIZE = 433;
    private final int[] framebuffer = new int[BUFFER_INT_SIZE];
    private int framesize;
    private final byte[] frame_bytes = new byte[BUFFER_INT_SIZE * 4];
    private int wordpointer;
    private int bitindex;
    private int syncword;
    private int header_pos = 0;
    private Float replayGainScale;
    private boolean single_ch_mode;
    private final int[] bitmask = {
            0, // dummy
            0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F, 0x0000003F, 0x0000007F, 0x000000FF, 0x000001FF, 0x000003FF,
            0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF, 0x00007FFF, 0x0000FFFF, 0x0001FFFF};

    private final PushbackInputStream source;

    private final Header header = new Header();

    private final byte[] syncbuf = new byte[4];

    private final Crc16[] crc = new Crc16[1];

    private byte[] rawid3v2 = null;

    private boolean firstframe;

    public Bitstream(InputStream in) {
        if (in == null) throw new NullPointerException("in");
        in = new BufferedInputStream(in);
        loadID3v2(in);
        firstframe = true;
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);

        closeFrame();
    }

    public int header_pos() {
        return header_pos;
    }

    private void loadID3v2(final InputStream in) {
        int size = -1;
        try {
            // Read ID3v2 header (10 bytes).
            in.mark(10);
            size = readID3v2Header(in);
            header_pos = size;
        } catch (IOException ignored) {
        } finally {
            try {
                // Unread ID3v2 header (10 bytes).
                in.reset();
            } catch (IOException ignored) {
            }
        }
        // Load ID3v2 tags.
        try {
            if (size > 0) {
                rawid3v2 = new byte[size];
                in.read(rawid3v2, 0, rawid3v2.length);
                parseID3v2Frames(rawid3v2);
            }
        } catch (IOException ignored) {
        }
    }

    private int readID3v2Header(final InputStream in) throws IOException {
        final byte[] id3header = new byte[4];
        int size = -10;
        in.read(id3header, 0, 3);
        // Look for ID3v2
        if (id3header[0] == 'I' && id3header[1] == 'D' && id3header[2] == '3') {
            in.read(id3header, 0, 3);
            in.read(id3header, 0, 4);
            size = (id3header[0] << 21) + (id3header[1] << 14) + (id3header[2] << 7) + id3header[3];
        }
        return size + 10;
    }

    public InputStream getRawID3v2() {
        if (rawid3v2 == null)
            return null;
        else {
            return new ByteArrayInputStream(rawid3v2);
        }
    }

    private void parseID3v2Frames(final byte[] bframes) {
        if (bframes == null) return;
        if (!"ID3".equals(new String(bframes, 0, 3))) return;
        final int v2version = (int) (bframes[3] & 0xFF);
        if (v2version < 2 || v2version > 4) {
            return;
        }
        try {
            Float replayGain = null, replayGainPeak = null;
            int size;
            String value = null;
            for (int i = 10; i < bframes.length && bframes[i] > 0; i += size) {
                if (v2version == 3 || v2version == 4) {
                    // ID3v2.3 & ID3v2.4
                    final String code = new String(bframes, i, 4);
                    size = bframes[i + 4] << 24 & 0xFF000000 | bframes[i + 5] << 16 & 0x00FF0000 | bframes[i + 6] << 8
                            & 0x0000FF00 | bframes[i + 7] & 0x000000FF;
                    i += 10;
                    if (code.equals("TXXX")) {
                        value = parseText(bframes, i, size, 1);
                        final String[] values = value.split("\0");
                        if (values.length == 2) {
                            final String name = values[0];
                            value = values[1];
                            if (name.equals("replaygain_track_peak")) {
                                replayGainPeak = Float.parseFloat(value);
                                if (replayGain != null) break;
                            } else if (name.equals("replaygain_track_gain")) {
                                replayGain = Float.parseFloat(value.replace(" dB", "")) + 3;
                                if (replayGainPeak != null) break;
                            }
                        }
                    }
                } else {
                    // ID3v2.2
                    String scode = new String(bframes, i, 3);
                    size = (bframes[i + 3] << 16) + (bframes[i + 4] << 8) + bframes[i + 5];
                    i += 6;
                    if (scode.equals("TXXX")) {
                        value = parseText(bframes, i, size, 1);
                        final String[] values = value.split("\0");
                        if (values.length == 2) {
                            final String name = values[0];
                            value = values[1];
                            if (name.equals("replaygain_track_peak")) {
                                replayGainPeak = Float.parseFloat(value);
                                if (replayGain != null) break;
                            } else if (name.equals("replaygain_track_gain")) {
                                replayGain = Float.parseFloat(value.replace(" dB", "")) + 3;
                                if (replayGainPeak != null) break;
                            }
                        }
                    }
                }
            }
            if (replayGain != null && replayGainPeak != null) {
                replayGainScale = (float) Math.pow(10, replayGain / 20f);
                // If scale * peak > 1 then reduce scale (preamp) to prevent clipping.
                replayGainScale = Math.min(1 / replayGainPeak, replayGainScale);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String parseText(final byte[] bframes, final int offset, final int size, final int skip) {
        String value = null;
        try {
            String[] ENC_TYPES = {"ISO-8859-1", "UTF16", "UTF-16BE", "UTF-8"};
            value = new String(bframes, offset + skip, size - skip, ENC_TYPES[bframes[offset]]);
        } catch (UnsupportedEncodingException ignored) {
        }
        return value;
    }

    public Float getReplayGainScale() {
        return replayGainScale;
    }

    public void close() throws BitstreamException {
        try {
            source.close();
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
    }

    public Header readFrame() throws BitstreamException {
        Header result = null;
        try {
            result = readNextFrame();
            // E.B, Parse VBR (if any) first frame.
            if (firstframe) {
                result.parseVBR(frame_bytes);
                firstframe = false;
            }
        } catch (BitstreamException ex) {
            if (ex.getErrorCode() == INVALIDFRAME)
                // Try to skip this frame.
                // System.out.println("INVALIDFRAME");
                try {
                    closeFrame();
                    result = readNextFrame();
                } catch (BitstreamException e) {
                    if (e.getErrorCode() != STREAM_EOF) // wrap original exception so stack trace is maintained.
                        throw newBitstreamException(e.getErrorCode(), e);
                }
            else if (ex.getErrorCode() != STREAM_EOF) // wrap original exception so stack trace is maintained.
                throw newBitstreamException(ex.getErrorCode(), ex);
        }
        return result;
    }

    private Header readNextFrame() throws BitstreamException {
        if (framesize == -1) nextFrame();
        return header;
    }

    private void nextFrame() throws BitstreamException {
        // entire frame is read by the header class.
        header.readHeader(this, crc);
    }

    public void unreadFrame() throws BitstreamException {
        if (wordpointer == -1 && bitindex == -1 && framesize > 0) try {
            source.unread(frame_bytes, 0, framesize);
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR);
        }
    }

    public void closeFrame() {
        framesize = -1;
        wordpointer = -1;
        bitindex = -1;
    }

    public boolean isSyncCurrentPosition(final int syncmode) throws BitstreamException {
        final int read = readBytes(syncbuf, 0, 4);
        final int headerstring = syncbuf[0] << 24 & 0xFF000000 | syncbuf[1] << 16 & 0x00FF0000 | syncbuf[2] << 8 & 0x0000FF00
                | syncbuf[3] & 0x000000FF;

        try {
            source.unread(syncbuf, 0, read);
        } catch (IOException ignored) {
        }

        boolean sync = false;
        switch (read) {
            case 0:
                sync = true;
                break;
            case 4:
                sync = isSyncMark(headerstring, syncmode, syncword);
                break;
        }

        return sync;
    }

    public int readBits(final int n) {
        return get_bits(n);
    }

    public int readCheckedBits(final int n) {
        // REVIEW: implement CRC check.
        return get_bits(n);
    }

    BitstreamException newBitstreamException(final int errorcode) {
        return new BitstreamException(errorcode, null);
    }

    private BitstreamException newBitstreamException(final int errorcode, final Throwable throwable) {
        return new BitstreamException(errorcode, throwable);
    }

    int syncHeader(final byte syncmode) throws BitstreamException {
        boolean sync;
        int headerstring;
        // read additional 2 bytes
        final int bytesRead = readBytes(syncbuf, 0, 3);
        if (bytesRead != 3) throw newBitstreamException(STREAM_EOF, null);
        headerstring = syncbuf[0] << 16 & 0x00FF0000 | syncbuf[1] << 8 & 0x0000FF00 | syncbuf[2] & 0x000000FF;
        do {
            headerstring <<= 8;
            if (readBytes(syncbuf, 3, 1) != 1) throw newBitstreamException(STREAM_EOF, null);
            headerstring |= syncbuf[3] & 0x000000FF;
            sync = isSyncMark(headerstring, syncmode, syncword);
        } while (!sync);
        // current_frame_number++;
        // if (last_frame_number < current_frame_number) last_frame_number = current_frame_number;
        return headerstring;
    }

    public boolean isSyncMark(final int headerstring, final int syncmode, final int word) {
        boolean sync;

        if (syncmode == INITIAL_SYNC) // sync = ((headerstring & 0xFFF00000) == 0xFFF00000);
            sync = (headerstring & 0xFFE00000) == 0xFFE00000; // SZD: MPEG 2.5
        else
            sync = (headerstring & 0xFFF80C00) == word && (headerstring & 0x000000C0) == 0x000000C0 == single_ch_mode;

        // filter out invalid sample rate
        if (sync) sync = (headerstring >>> 10 & 3) != 3;
        // filter out invalid layer
        if (sync) sync = (headerstring >>> 17 & 3) != 0;
        // filter out invalid version
        if (sync) sync = (headerstring >>> 19 & 3) != 1;

        return sync;
    }

    int read_frame_data(final int bytesize) throws BitstreamException {
        int numread = 0;
        numread = readFully(frame_bytes, 0, bytesize);
        framesize = bytesize;
        wordpointer = -1;
        bitindex = -1;
        return numread;
    }

    void parse_frame() throws BitstreamException {
        // Convert Bytes read to int
        int b = 0;
        final byte[] byteread = frame_bytes;
        final int bytesize = framesize;

        for (int k = 0; k < bytesize; k = k + 4) {
            byte b0 = 0;
            byte b1 = 0;
            byte b2 = 0;
            byte b3 = 0;
            b0 = byteread[k];
            if (k + 1 < bytesize) b1 = byteread[k + 1];
            if (k + 2 < bytesize) b2 = byteread[k + 2];
            if (k + 3 < bytesize) b3 = byteread[k + 3];
            framebuffer[b++] = b0 << 24 & 0xFF000000 | b1 << 16 & 0x00FF0000 | b2 << 8 & 0x0000FF00 | b3 & 0x000000FF;
        }
        wordpointer = 0;
        bitindex = 0;
    }

    public int get_bits(final int number_of_bits) {
        int returnvalue = 0;
        final int sum = bitindex + number_of_bits;
        if (wordpointer < 0) wordpointer = 0;

        if (sum <= 32) {
            returnvalue = framebuffer[wordpointer] >>> 32 - sum & bitmask[number_of_bits];
            // returnvalue = (wordpointer[0] >> (32 - sum)) & bitmask[number_of_bits];
            if ((bitindex += number_of_bits) == 32) {
                bitindex = 0;
                wordpointer++; // added by me!
            }
            return returnvalue;
        }

        final int Right = framebuffer[wordpointer] & 0x0000FFFF;
        wordpointer++;
        final int Left = framebuffer[wordpointer] & 0xFFFF0000;
        returnvalue = Right << 16 & 0xFFFF0000 | Left >>> 16 & 0x0000FFFF;

        returnvalue >>>= 48 - sum; // returnvalue >>= 16 - (number_of_bits - (32 - bitindex))
        returnvalue &= bitmask[number_of_bits];
        bitindex = sum - 32;
        return returnvalue;
    }

    void set_syncword(final int syncword0) {
        syncword = syncword0 & 0xFFFFFF3F;
        single_ch_mode = (syncword0 & 0x000000C0) == 0x000000C0;
    }

    private int readFully(final byte[] b, int offs, int len) throws BitstreamException {
        int nRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) {
                    while (len-- > 0)
                        b[offs++] = 0;
                    break;
                    // throw newBitstreamException(UNEXPECTED_EOF, new EOFException());
                }
                nRead = nRead + bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return nRead;
    }

    private int readBytes(final byte[] b, int offs, int len) throws BitstreamException {
        int totalBytesRead = 0;
        try {
            while (len > 0) {
                int bytesread = source.read(b, offs, len);
                if (bytesread == -1) break;
                totalBytesRead += bytesread;
                offs += bytesread;
                len -= bytesread;
            }
        } catch (IOException ex) {
            throw newBitstreamException(STREAM_ERROR, ex);
        }
        return totalBytesRead;
    }

    static public final int BITSTREAM_ERROR = 0x100;

    static public final int UNKNOWN_ERROR = BITSTREAM_ERROR;

    static public final int UNKNOWN_SAMPLE_RATE = BITSTREAM_ERROR + 1;

    static public final int STREAM_ERROR = BITSTREAM_ERROR + 2;

    static public final int UNEXPECTED_EOF = BITSTREAM_ERROR + 3;

    static public final int STREAM_EOF = BITSTREAM_ERROR + 4;

    static public final int INVALIDFRAME = BITSTREAM_ERROR + 5;

    static public final int BITSTREAM_LAST = 0x1ff;
}
