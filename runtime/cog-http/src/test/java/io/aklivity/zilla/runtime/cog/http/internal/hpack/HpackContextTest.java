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
package io.aklivity.zilla.runtime.cog.http.internal.hpack;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HpackContextTest
{
    private static final String[][] STATIC_TABLE =
    {
        /* 0  */ {null, null},
        /* 1  */ {":authority", null},
        /* 2  */ {":method", "GET"},
        /* 3  */ {":method", "POST"},
        /* 4  */ {":path", "/"},
        /* 5  */ {":path", "/index.html"},
        /* 6  */ {":scheme", "http"},
        /* 7  */ {":scheme", "https"},
        /* 8  */ {":status", "200"},
        /* 9  */ {":status", "204"},
        /* 10 */ {":status", "206"},
        /* 11 */ {":status", "304"},
        /* 12 */ {":status", "400"},
        /* 13 */ {":status", "404"},
        /* 14 */ {":status", "500"},
        /* 15 */ {"accept-charset", null},
        /* 16 */ {"accept-encoding", "gzip, deflate"},
        /* 17 */ {"accept-language", null},
        /* 18 */ {"accept-ranges", null},
        /* 19 */ {"accept", null},
        /* 20 */ {"access-control-allow-origin", null},
        /* 21 */ {"age", null},
        /* 22 */ {"allow", null},
        /* 23 */ {"authorization", null},
        /* 24 */ {"cache-control", null},
        /* 25 */ {"content-disposition", null},
        /* 26 */ {"content-encoding", null},
        /* 27 */ {"content-language", null},
        /* 28 */ {"content-length", null},
        /* 29 */ {"content-location", null},
        /* 30 */ {"content-range", null},
        /* 31 */ {"content-type", null},
        /* 32 */ {"cookie", null},
        /* 33 */ {"date", null},
        /* 34 */ {"etag", null},
        /* 35 */ {"expect", null},
        /* 36 */ {"expires", null},
        /* 37 */ {"from", null},
        /* 38 */ {"host", null},
        /* 39 */ {"if-match", null},
        /* 40 */ {"if-modified-since", null},
        /* 41 */ {"if-none-match", null},
        /* 42 */ {"if-range", null},
        /* 43 */ {"if-unmodified-since", null},
        /* 44 */ {"last-modified", null},
        /* 45 */ {"link", null},
        /* 46 */ {"location", null},
        /* 47 */ {"max-forwards", null},
        /* 48 */ {"proxy-authenticate", null},
        /* 49 */ {"proxy-authorization", null},
        /* 50 */ {"range", null},
        /* 51 */ {"referer", null},
        /* 52 */ {"refresh", null},
        /* 53 */ {"retry-after", null},
        /* 54 */ {"server", null},
        /* 55 */ {"set-cookie", null},
        /* 56 */ {"strict-transport-security", null},
        /* 57 */ {"transfer-encoding", null},
        /* 58 */ {"user-agent", null},
        /* 59 */ {"vary", null},
        /* 60 */ {"via", null},
        /* 61 */ {"www-authenticate", null},
    };

    @Test
    public void encodeStatic()
    {
        HpackContext context = new HpackContext();

        assertEquals(1, context.index(":authority"));
        assertEquals(2, context.index(":method"));
        assertEquals(4, context.index(":path"));
        assertEquals(6, context.index(":scheme"));
        assertEquals(8, context.index(":status"));
        for (int i = 15; i < STATIC_TABLE.length; i++)
        {
            assertEquals(i, context.index(STATIC_TABLE[i][0]));
        }

        for (int i = 2; i < 15; i++)
        {
            assertEquals(i, context.index(STATIC_TABLE[i][0], STATIC_TABLE[i][1]));
        }
        assertEquals(16, context.index("accept-encoding", "gzip, deflate"));
    }

    @Test
    public void encodeDynamic()
    {
        HpackContext context = new HpackContext(150, true);
        context.add("name1", "value1");
        assertEquals(62, context.index("name1"));
        assertEquals(62, context.index("name1", "value1"));

        context.add("name2", "value2");
        assertEquals(62, context.index("name2"));
        assertEquals(62, context.index("name2", "value2"));
        assertEquals(63, context.index("name1"));
        assertEquals(63, context.index("name1", "value1"));

        context.add("name3", "value3");
        assertEquals(62, context.index("name3"));
        assertEquals(62, context.index("name3", "value3"));
        assertEquals(63, context.index("name2"));
        assertEquals(63, context.index("name2", "value2"));
        assertEquals(64, context.index("name1"));
        assertEquals(64, context.index("name1", "value1"));

        context.add("name4", "value4");
        assertEquals(62, context.index("name4"));
        assertEquals(62, context.index("name4", "value4"));
        assertEquals(63, context.index("name3"));
        assertEquals(63, context.index("name3", "value3"));
        assertEquals(64, context.index("name2"));
        assertEquals(64, context.index("name2", "value2"));
        assertEquals(-1, context.index("name1"));
        assertEquals(-1, context.index("name1", "value1"));

        context.add("name5", "value5");
        assertEquals(62, context.index("name5"));
        assertEquals(62, context.index("name5", "value5"));
        assertEquals(63, context.index("name4"));
        assertEquals(63, context.index("name4", "value4"));
        assertEquals(64, context.index("name3"));
        assertEquals(64, context.index("name3", "value3"));
        assertEquals(-1, context.index("name2"));
        assertEquals(-1, context.index("name2", "value2"));
        assertEquals(-1, context.index("name1"));
        assertEquals(-1, context.index("name1", "value1"));

        context.add("name6", "value66666666666666666666666666666666");
        assertEquals(62, context.index("name6"));
        assertEquals(62, context.index("name6", "value66666666666666666666666666666666"));
        assertEquals(63, context.index("name5"));
        assertEquals(63, context.index("name5", "value5"));
        assertEquals(-1, context.index("name4"));
        assertEquals(-1, context.index("name4", "value4"));
        assertEquals(-1, context.index("name3"));
        assertEquals(-1, context.index("name3", "value3"));
        assertEquals(-1, context.index("name2"));
        assertEquals(-1, context.index("name2", "value2"));
        assertEquals(-1, context.index("name1"));
        assertEquals(-1, context.index("name1", "value1"));

    }

}
