/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.cog.http2.internal.hpack;

import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackHeaderFieldFW.HeaderFieldType.LITERAL;
import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.NEVER_INDEXED;
import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackLiteralHeaderFieldFW.LiteralType.WITHOUT_INDEXING;
import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackLiteralHeaderFieldFW.NameType.INDEXED;
import static io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackLiteralHeaderFieldFW.NameType.NEW;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.cog.http2.internal.hpack.HpackHeaderFieldFW.HeaderFieldType;

public class HpackHeaderFieldFWTest
{

    // Decoding "C.2.1.  Literal Header Field with Indexing"
    @Test
    public void decodeC21()
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header field  begin
                        "400a637573746f6d2d6b65790d637573746f6d2d686561646572" +
                        // Header field  end
                        "00");
        DirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW().wrap(buf, 1, buf.capacity() - 1);
        assertEquals(27, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(INCREMENTAL_INDEXING, literalRO.literalType());
        assertEquals(NEW, literalRO.nameType());

        HpackStringFW nameRO = literalRO.nameLiteral();
        DirectBuffer name = nameRO.payload();
        assertEquals("custom-key", name.getStringWithoutLengthUtf8(0, name.capacity()));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("custom-header", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    // Encoding "C.2.1.  Literal Header Field with Indexing"
    @Test
    public void encodeC21()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .literal(x -> x.type(INCREMENTAL_INDEXING).name("custom-key").value("custom-header"))
                .build();

        assertEquals(27, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(INCREMENTAL_INDEXING, literalRO.literalType());
        assertEquals(NEW, literalRO.nameType());

        HpackStringFW nameRO = literalRO.nameLiteral();
        DirectBuffer name = nameRO.payload();
        assertEquals("custom-key", name.getStringWithoutLengthUtf8(0, name.capacity()));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("custom-header", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }


    // Decoding "C.2.2.  Literal Header Field without Indexing"
    @Test
    public void decodeC22()
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header field  begin
                        "040c2f73616d706c652f70617468" +
                        // Header field  end
                        "00");
        DirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW().wrap(buf, 1, buf.capacity() - 1);
        assertEquals(15, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(WITHOUT_INDEXING, literalRO.literalType());
        assertEquals(INDEXED, literalRO.nameType());

        int index = literalRO.nameIndex();
        assertEquals(":path", new HpackContext().name(index));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("/sample/path", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    // Encoding "C.2.2.  Literal Header Field without Indexing"
    @Test
    public void encodeC22()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .literal(x -> x.type(WITHOUT_INDEXING).name(4).value("/sample/path"))
                .build();

        assertEquals(15, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(WITHOUT_INDEXING, literalRO.literalType());
        assertEquals(INDEXED, literalRO.nameType());

        int index = literalRO.nameIndex();
        assertEquals(":path", new HpackContext().name(index));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("/sample/path", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    // Decoding "C.2.3.  Literal Header Field Never Indexed"
    @Test
    public void decodeC23()
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header field  begin
                        "100870617373776f726406736563726574" +
                        // Header field  end
                        "00");
        DirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW().wrap(buf, 1, buf.capacity() - 1);
        assertEquals(18, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(NEVER_INDEXED, literalRO.literalType());
        assertEquals(NEW, literalRO.nameType());

        HpackStringFW nameRO = literalRO.nameLiteral();
        DirectBuffer name = nameRO.payload();
        assertEquals("password", name.getStringWithoutLengthUtf8(0, name.capacity()));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("secret", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    // Decoding "C.2.3.  Literal Header Field Never Indexed"
    @Test
    public void encodeC23()
    {
        DirectBuffer password = new UnsafeBuffer("password".getBytes(UTF_8));
        DirectBuffer secret = new UnsafeBuffer("secret".getBytes(UTF_8));

        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .literal(x -> x.type(NEVER_INDEXED)
                               .name(password, 0, password.capacity())
                               .value(secret, 0, secret.capacity()))
                .build();

        assertEquals(18, fw.limit());

        assertEquals(LITERAL, fw.type());
        HpackLiteralHeaderFieldFW literalRO = fw.literal();

        assertEquals(NEVER_INDEXED, literalRO.literalType());
        assertEquals(NEW, literalRO.nameType());

        HpackStringFW nameRO = literalRO.nameLiteral();
        DirectBuffer name = nameRO.payload();
        assertEquals("password", name.getStringWithoutLengthUtf8(0, name.capacity()));

        HpackStringFW valueRO = literalRO.valueLiteral();
        DirectBuffer value = valueRO.payload();
        assertEquals("secret", value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    // Decoding "C.2.4.  Indexed Header Field"
    @Test
    public void decodeC24()
    {
        byte[] bytes = BitUtil.fromHex(
                "00" +  // +00 to test offset
                        // Header field  begin
                        "82" +
                        // Header field  end
                        "00");
        DirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW().wrap(buf, 1, buf.capacity() - 1);
        assertEquals(2, fw.limit());

        assertEquals(HeaderFieldType.INDEXED, fw.type());
        int index = fw.index();
        HpackContext context = new HpackContext();
        assertEquals(":method", context.name(index));
        assertEquals("GET", context.value(index));
    }

    // Encoding "C.2.4.  Indexed Header Field"
    @Test
    public void encodeC24()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        HpackHeaderFieldFW fw = new HpackHeaderFieldFW.Builder()
                .wrap(buf, 1, buf.capacity())
                .indexed(2)
                .build();

        assertEquals(2, fw.limit());
        assertEquals(HeaderFieldType.INDEXED, fw.type());
        int index = fw.index();
        assertEquals(2, index);
        HpackContext context = new HpackContext();
        assertEquals(":method", context.name(index));
        assertEquals("GET", context.value(index));
    }

}
