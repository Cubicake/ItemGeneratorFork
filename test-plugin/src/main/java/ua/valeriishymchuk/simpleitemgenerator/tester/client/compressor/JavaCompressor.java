/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.valeriishymchuk.simpleitemgenerator.tester.client.compressor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Implements deflate compression by wrapping {@link Deflater} and {@link Inflater}.
 */
public class JavaCompressor {

    private static final int ZLIB_BUFFER_SIZE = 8192;

    private final Deflater deflater;
    private final Inflater inflater;
    private boolean disposed = false;

    private JavaCompressor(int level) {
        this.deflater = new Deflater(level);
        this.inflater = new Inflater();
    }

    public void inflate(ByteBuf source, ByteBuf destination, int uncompressedSize)
            throws DataFormatException {
        ensureNotDisposed();

        // We (probably) can't nicely deal with >=1 buffer nicely, so let's scream loudly.
        checkArgument(source.nioBufferCount() == 1, "source has multiple backing buffers");
        checkArgument(destination.nioBufferCount() == 1, "destination has multiple backing buffers");

        final int origIdx = source.readerIndex();
        byte[] buffer = new byte[source.readableBytes()];
        source.getBytes(origIdx, buffer);
        inflater.setInput(buffer);
        //inflater.setInput(source.nioBuffer());

        try {
            while (!inflater.finished() && inflater.getBytesWritten() < uncompressedSize) {
                if (!destination.isWritable()) {
                    destination.ensureWritable(ZLIB_BUFFER_SIZE);
                }

                ByteBuffer destNioBuf = destination.nioBuffer(destination.writerIndex(),
                        destination.writableBytes());
                byte[] dest = new byte[destination.writableBytes()];
                int produced = inflater.inflate(dest);
                destNioBuf.put(dest, 0, produced);
                destination.writerIndex(destination.writerIndex() + produced);
            }

            if (!inflater.finished()) {
                throw new DataFormatException("Received a deflate stream that was too large, wanted "
                        + uncompressedSize);
            }
            source.readerIndex(origIdx + inflater.getTotalIn());
        } finally {
            inflater.reset();
        }
    }

    public void deflate(ByteBuf source, ByteBuf destination) throws DataFormatException {
        ensureNotDisposed();

        // We (probably) can't nicely deal with >=1 buffer nicely, so let's scream loudly.
        checkArgument(source.nioBufferCount() == 1, "source has multiple backing buffers");
        checkArgument(destination.nioBufferCount() == 1, "destination has multiple backing buffers");

        final int origIdx = source.readerIndex();
        byte[] buffer = new byte[source.readableBytes()];
        source.getBytes(origIdx, buffer);
        deflater.setInput(buffer);
        deflater.finish();

        while (!deflater.finished()) {
            if (!destination.isWritable()) {
                destination.ensureWritable(ZLIB_BUFFER_SIZE);
            }

            ByteBuffer destNioBuf = destination.nioBuffer(destination.writerIndex(),
                    destination.writableBytes());
            byte[] dest = new byte[destination.writableBytes()];
            int produced = inflater.inflate(dest);
            destNioBuf.put(dest, 0, produced);
            destination.writerIndex(destination.writerIndex() + produced);
        }

        source.readerIndex(origIdx + deflater.getTotalIn());
        deflater.reset();
    }

    public void close() {
        disposed = true;
        deflater.end();
        inflater.end();
    }

    private void ensureNotDisposed() {
        checkState(!disposed, "Object already disposed");
    }

}