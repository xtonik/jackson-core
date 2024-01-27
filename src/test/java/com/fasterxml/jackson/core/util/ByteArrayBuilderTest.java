package com.fasterxml.jackson.core.util;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;

import static com.fasterxml.jackson.core.util.ByteArrayBuilder.MAX_JAVA_ARRAY_SIZE;

public class ByteArrayBuilderTest extends com.fasterxml.jackson.core.BaseTest
{
    public void testSimple() throws Exception
    {
        ByteArrayBuilder b = new ByteArrayBuilder(null, 20);
        Assert.assertArrayEquals(new byte[0], b.toByteArray());

        b.write((byte) 0);
        b.append(1);

        byte[] foo = new byte[98];
        for (int i = 0; i < foo.length; ++i) {
            foo[i] = (byte) (2 + i);
        }
        b.write(foo);

        byte[] result = b.toByteArray();
        assertEquals(100, result.length);
        for (int i = 0; i < 100; ++i) {
            assertEquals(i, (int) result[i]);
        }

        b.release();
        b.close();
    }

    // [core#1195]: Try to verify that BufferRecycler instance is indeed reused
    public void testBufferRecyclerReuse() throws Exception
    {
        JsonFactory f = new JsonFactory();
        BufferRecycler br = new BufferRecycler()
                // need to link with some pool
                .withPool(JsonRecyclerPools.newBoundedPool(3));

        ByteArrayBuilder bab = new ByteArrayBuilder(br, 20);
        assertSame(br, bab.bufferRecycler());

        JsonGenerator g = f.createGenerator(bab);
        IOContext ioCtxt = ((GeneratorBase) g).ioContext();
        assertSame(br, ioCtxt.bufferRecycler());
        assertTrue(ioCtxt.bufferRecycler().isLinkedWithPool());

        g.writeStartArray();
        g.writeEndArray();
        g.close();

        // Generator.close() should NOT release buffer recycler
        assertTrue(br.isLinkedWithPool());

        byte[] result = bab.getClearAndRelease();
        assertEquals("[]", new String(result, StandardCharsets.UTF_8));
        // Nor accessing contents
        assertTrue(br.isLinkedWithPool());

        // only explicit release does
        br.releaseToPool();
        assertFalse(br.isLinkedWithPool());
    }

    public void testMaxArraySize_overJavaArrayMaxSize() {
        int totalSize = Integer.MAX_VALUE - 1;
        maxArraySize(totalSize);
    }

    public void testMaxArraySize_overSpecifiedLimit() {
        int totalSize = MAX_JAVA_ARRAY_SIZE + 1;
        maxArraySize(totalSize);
    }

    private static void maxArraySize(int totalSize) {
        try(ByteArrayBuilder bab = new ByteArrayBuilder(totalSize - 1)) {
            prepareBuilder(bab, totalSize);

            bab.finishCurrentSegment(); // causes reallocation
            fail("Exception should be thrown");
        } catch (IllegalStateException e) {
            // expected, do nothing
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getClass());
        }
    }

    private static void prepareBuilder(ByteArrayBuilder bab, int totalSize) {
        // split to smaller arrays to avoid java.lang.OutOfMemoryError: Java heap space
        byte[] bytes = new byte[MAX_JAVA_ARRAY_SIZE / 4 + 1];
        bab.write(bytes);
        bab.write(bytes);
        bab.write(bytes);
        bab.write(bytes);
        for (int i = 0; i < totalSize - MAX_JAVA_ARRAY_SIZE - 1; i++) {
            bab.append(1);
        }
        assert totalSize == bab.size() : "Array builder should have size = " + totalSize + ", not " + bab.size();
    }
}
