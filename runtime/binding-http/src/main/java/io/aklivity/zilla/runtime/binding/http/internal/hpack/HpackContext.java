/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.hpack;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class HpackContext
{
    private static final DirectBuffer EMPTY_VALUE = new UnsafeBuffer(new byte[0]);

    private static final HeaderField[] STATIC_TABLE =
    {
        /* 0  */ new HeaderField((String) null, null),
        /* 1  */ new HeaderField(":authority", null),
        /* 2  */ new HeaderField(":method", "GET"),
        /* 3  */ new HeaderField(":method", "POST"),
        /* 4  */ new HeaderField(":path", "/"),
        /* 5  */ new HeaderField(":path", "/index.html"),
        /* 6  */ new HeaderField(":scheme", "http"),
        /* 7  */ new HeaderField(":scheme", "https"),
        /* 8  */ new HeaderField(":status", "200"),
        /* 9  */ new HeaderField(":status", "204"),
        /* 10 */ new HeaderField(":status", "206"),
        /* 11 */ new HeaderField(":status", "304"),
        /* 12 */ new HeaderField(":status", "400"),
        /* 13 */ new HeaderField(":status", "404"),
        /* 14 */ new HeaderField(":status", "500"),
        /* 15 */ new HeaderField("accept-charset", null),
        /* 16 */ new HeaderField("accept-encoding", "gzip, deflate"),
        /* 17 */ new HeaderField("accept-language", null),
        /* 18 */ new HeaderField("accept-ranges", null),
        /* 19 */ new HeaderField("accept", null),
        /* 20 */ new HeaderField("access-control-allow-origin", null),
        /* 21 */ new HeaderField("age", null),
        /* 22 */ new HeaderField("allow", null),
        /* 23 */ new HeaderField("authorization", null),
        /* 24 */ new HeaderField("cache-control", null),
        /* 25 */ new HeaderField("content-disposition", null),
        /* 26 */ new HeaderField("content-encoding", null),
        /* 27 */ new HeaderField("content-language", null),
        /* 28 */ new HeaderField("content-length", null),
        /* 29 */ new HeaderField("content-location", null),
        /* 30 */ new HeaderField("content-range", null),
        /* 31 */ new HeaderField("content-type", null),
        /* 32 */ new HeaderField("cookie", null),
        /* 33 */ new HeaderField("date", null),
        /* 34 */ new HeaderField("etag", null),
        /* 35 */ new HeaderField("expect", null),
        /* 36 */ new HeaderField("expires", null),
        /* 37 */ new HeaderField("from", null),
        /* 38 */ new HeaderField("host", null),
        /* 39 */ new HeaderField("if-match", null),
        /* 40 */ new HeaderField("if-modified-since", null),
        /* 41 */ new HeaderField("if-none-match", null),
        /* 42 */ new HeaderField("if-range", null),
        /* 43 */ new HeaderField("if-unmodified-since", null),
        /* 44 */ new HeaderField("last-modified", null),
        /* 45 */ new HeaderField("link", null),
        /* 46 */ new HeaderField("location", null),
        /* 47 */ new HeaderField("max-forwards", null),
        /* 48 */ new HeaderField("proxy-authenticate", null),
        /* 49 */ new HeaderField("proxy-authorization", null),
        /* 50 */ new HeaderField("range", null),
        /* 51 */ new HeaderField("referer", null),
        /* 52 */ new HeaderField("refresh", null),
        /* 53 */ new HeaderField("retry-after", null),
        /* 54 */ new HeaderField("server", null),
        /* 55 */ new HeaderField("set-cookie", null),
        /* 56 */ new HeaderField("strict-transport-security", null),
        /* 57 */ new HeaderField("transfer-encoding", null),
        /* 58 */ new HeaderField("user-agent", null),
        /* 59 */ new HeaderField("vary", null),
        /* 60 */ new HeaderField("via", null),
        /* 61 */ new HeaderField("www-authenticate", null)
    };

    private static final int STATIC_TABLE_LENGTH = STATIC_TABLE.length;

    public static final DirectBuffer CONNECTION = new UnsafeBuffer("connection".getBytes(UTF_8));
    public static final DirectBuffer CONTENT_LENGTH = new UnsafeBuffer("content-length".getBytes(UTF_8));
    public static final DirectBuffer TE = new UnsafeBuffer("te".getBytes(UTF_8));
    public static final DirectBuffer TRAILERS = new UnsafeBuffer("trailers".getBytes(UTF_8));
    public static final DirectBuffer KEEP_ALIVE = new UnsafeBuffer("keep-alive".getBytes(UTF_8));
    public static final DirectBuffer PROXY_CONNECTION = new UnsafeBuffer("proxy-connection".getBytes(UTF_8));
    public static final DirectBuffer UPGRADE = new UnsafeBuffer("upgrade".getBytes(UTF_8));
    public static final DirectBuffer DEFAULT_ACCESS_CONTROL_ALLOW_ORIGIN = new UnsafeBuffer("*".getBytes(UTF_8));

    // Dynamic table. Entries are added at the end (since it is in reverse order,
    // need to calculate the index accordingly)
    /* private */ final List<HeaderField> table = new ArrayList<>();
    /* private */ int tableSize;

    // No need to update the following index maps for decoding context
    private final boolean encoding;

    // name --> uniquie id (stable across evictions) for dynamic entries.
    // Used during encoding
    private final Map<DirectBuffer, Long> name2Index = new HashMap<>();

    // (name, value) --> uniquie id (stable across evictions) for dynamic entries.
    // Used during encoding
    private final Map<NameValue, Long> namevalue2Index = new HashMap<>();

    private int maxTableSize;

    // Keeps track of number of evictions and used in calculation of unique id
    // (No need to worry about overflow as it takes many years to overflow in practice)
    private long noEvictions;

    private static final class HeaderField
    {
        private final DirectBuffer name;
        private final DirectBuffer value;
        private int size;

        HeaderField(String name, String value)
        {
            this(buffer(name), buffer(value));
        }

        HeaderField(DirectBuffer name, DirectBuffer value)
        {
            this.name = requireNonNull(name);
            this.value = requireNonNull(value);
            this.size = name.capacity() + value.capacity() + 32;
        }

        private static DirectBuffer buffer(String str)
        {
            return str == null ? EMPTY_VALUE : new UnsafeBuffer(str.getBytes(UTF_8));
        }
    }

    public HpackContext()
    {
        this(4096, true);
    }

    public HpackContext(int maxTableSize, boolean encoding)
    {
        this.maxTableSize = maxTableSize;
        this.encoding = encoding;
    }

    void add(String name, String value)
    {
        DirectBuffer nameBuffer = new UnsafeBuffer(name.getBytes(UTF_8));
        DirectBuffer valueBuffer = new UnsafeBuffer(value.getBytes(UTF_8));

        add(nameBuffer, valueBuffer);
    }

    public void add(DirectBuffer nameBuffer, DirectBuffer valueBuffer)
    {
        HeaderField header = new HeaderField(nameBuffer, valueBuffer);

        // See if the header can be added to dynamic table. Calculate the
        // number of entries to be evicted to make space in the table.
        int noEntries = 0;
        int wouldbeSize = tableSize + header.size;
        while (noEntries < table.size() && wouldbeSize > maxTableSize)
        {
            wouldbeSize -= table.get(noEntries).size;
            noEntries++;
        }
        if (noEntries > 0)
        {
            evict(noEntries);
        }

        // After evicting older entries, add the current one if space available
        boolean spaceAvailable = wouldbeSize <= maxTableSize;
        if (spaceAvailable)
        {
            if (encoding)
            {
                long id = noEvictions + table.size();

                name2Index.putIfAbsent(header.name, id);

                NameValue nameValue = new NameValue(header.name, header.value);
                namevalue2Index.putIfAbsent(nameValue, id);
            }
            table.add(header);
            tableSize += header.size;
        }
    }

    public void updateSize(int newMaxTableSize)
    {
        if (newMaxTableSize < maxTableSize)
        {
            // Calculate the number of entries to be evicted so that table size is
            // under new max table size
            int wouldbeSize = maxTableSize;
            int noEntries = 0;

            while (noEntries < table.size() && wouldbeSize > newMaxTableSize)
            {
                wouldbeSize -= table.get(noEntries).size;
                noEntries++;
            }
            if (noEntries > 0)
            {
                evict(noEntries);
            }

        }

        this.maxTableSize = newMaxTableSize;
    }

    // Evicts older entries from dynamic table
    private void evict(int noEntries)
    {
        for (int i = 0; i < noEntries; i++)
        {
            HeaderField header = table.get(i);
            tableSize -= header.size;

            if (encoding)
            {
                Long id = noEvictions + i;
                if (id.equals(name2Index.get(header.name)))
                {
                    name2Index.remove(header.name, id);

                }
                NameValue nameValue = new NameValue(header.name, header.value);
                if (id.equals(namevalue2Index.get(nameValue)))
                {
                    namevalue2Index.remove(nameValue, id);
                }
            }
        }

        table.subList(0, noEntries).clear();
        noEvictions += noEntries;
    }

    // @return true if the index is valid
    //         false otherwise
    public boolean valid(int index)
    {
        return index != 0 && index < STATIC_TABLE.length + table.size();
    }

    String name(int index)
    {
        DirectBuffer nameBuffer = nameBuffer(index);
        return nameBuffer.getStringWithoutLengthUtf8(0, nameBuffer.capacity());
    }

    public DirectBuffer nameBuffer(int index)
    {
        if (!valid(index))
        {
            throw new IllegalArgumentException("Invalid index = " + index + " in HPACK context");
        }
        return index < STATIC_TABLE.length
                ? STATIC_TABLE[index].name
                : table.get(table.size() - (index - STATIC_TABLE_LENGTH) - 1).name;
    }

    String value(int index)
    {
        DirectBuffer valueBuffer = valueBuffer(index);
        return valueBuffer.getStringWithoutLengthUtf8(0, valueBuffer.capacity());
    }

    public DirectBuffer valueBuffer(int index)
    {
        if (!valid(index))
        {
            throw new IllegalArgumentException("Invalid index = " + index + " in HPACK context");
        }
        return index < STATIC_TABLE.length
                ? STATIC_TABLE[index].value
                : table.get(table.size() - (index - STATIC_TABLE_LENGTH) - 1).value;
    }

    int index(String name)
    {
        DirectBuffer nameBuffer = new UnsafeBuffer(name.getBytes(UTF_8));
        return index(nameBuffer);
    }

    public int index(DirectBuffer name)
    {
        int index = staticIndex(name);
        // If there is no entry in static table, look in dynamic table
        if (index == -1)
        {
            Long id = name2Index.get(name);
            index = (id != null) ? idToIndex(id) : -1;
        }
        return index;
    }

    int index(String name, String value)
    {
        DirectBuffer nameBuffer = new UnsafeBuffer(name.getBytes(UTF_8));
        DirectBuffer valueBuffer = new UnsafeBuffer(value.getBytes(UTF_8));

        return index(nameBuffer, valueBuffer);
    }

    public int index(DirectBuffer name, DirectBuffer value)
    {
        int index = staticIndex(name, value);
        // If there is no entry in static table, look in dynamic table
        if (index == -1)
        {
            Long id = namevalue2Index.get(new NameValue(name, value));
            return (id != null) ? idToIndex(id) : -1;
        }
        return index;
    }

    private int idToIndex(long id)
    {
        return (int) (STATIC_TABLE_LENGTH + table.size() - (id - noEvictions) - 1);

    }

    private static final class NameValue
    {
        private final DirectBuffer name;
        private final DirectBuffer value;
        private final int hash;

        NameValue(DirectBuffer name, DirectBuffer value)
        {
            this.name = name;
            this.value = value;
            this.hash = Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof NameValue)
            {
                NameValue other = (NameValue) obj;
                return Objects.equals(name, other.name) && Objects.equals(value, other.value);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return hash;
        }
    }

    /*
     * Index in static table for (name, value). There aren't many entries with value
     * in the static table, so simple loop for the entries corresponding to the given name.
     *
     * @return index in static table if present
     *         -1 otherwise
     */
    private static int staticIndex(DirectBuffer name, DirectBuffer value)
    {
        if (name.equals(STATIC_TABLE[16].name) && value.equals(STATIC_TABLE[16].value))
        {
            return 16;                              // accept-encoding: gzip, deflate
        }

        int start = -1, end = -1;
        if (name.getByte(0) == ':')
        {
            int index = staticIndex(name);
            switch (index)
            {
            case 2:                             // :method
                start = 2;                      // GET
                end = 3;                        // POST
                break;
            case 4:                             // :path
                start = 4;                      // /
                end = 5;                        // /index.html
                break;
            case 6:                             // :scheme
                start = 6;                      // http
                end = 7;                        // https
                break;
            case 8:                             // :status
                start = 8;                      // 200, 204, 206, 304,
                end = 14;                       // 400, 404, 500
                break;
            }
            if (start != -1)
            {
                for (int i = start; i <= end; i++)
                {
                    if (value.equals(STATIC_TABLE[i].value))
                    {
                        return i;
                    }
                }
            }

        }

        return -1;
    }

    /*
     * Index in static table for the name. There aren't many entries, so will use
     * the length and last byte in the name to look up the entry.
     *
     * @return index in static table if present
     *         -1 otherwise
     */
    private static int staticIndex(DirectBuffer name)
    {
        switch (name.capacity())
        {
        case 3: return staticIndex3(name);
        case 4: return staticIndex4(name);
        case 5: return staticIndex5(name);
        case 6: return staticIndex6(name);
        case 7: return staticIndex7(name);
        case 8: return staticIndex8(name);
        case 10: return staticIndex10(name);
        case 11: return staticIndex11(name);
        case 12: return staticIndex12(name);
        case 13: return staticIndex13(name);
        case 14: return staticIndex14(name);
        case 15: return staticIndex15(name);
        case 16: return staticIndex16(name);
        case 17: return staticIndex17(name);
        case 18: return staticIndex18(name);
        case 19: return staticIndex19(name);
        case 25: return staticIndex25(name);
        case 27: return staticIndex27(name);
        default: return -1;
        }
    }

    // Index in static table for the given name of length 3
    private static int staticIndex3(DirectBuffer name)
    {
        switch (name.getByte(2))
        {
        case 'a':
            if (STATIC_TABLE[60].name.equals(name))        // via
            {
                return 60;
            }
            break;
        case 'e':
            if (STATIC_TABLE[21].name.equals(name))        // age
            {
                return 21;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 4
    private static int staticIndex4(DirectBuffer name)
    {
        switch (name.getByte(3))
        {
        case 'e':
            if (STATIC_TABLE[33].name.equals(name))    // date
            {
                return 33;
            }
            break;
        case 'g':
            if (STATIC_TABLE[34].name.equals(name))    // etag
            {
                return 34;
            }
            break;
        case 'k':
            if (STATIC_TABLE[45].name.equals(name))    // link
            {
                return 45;
            }
            break;
        case 'm':
            if (STATIC_TABLE[37].name.equals(name))    // from
            {
                return 37;
            }
            break;
        case 't':
            if (STATIC_TABLE[38].name.equals(name))    // host
            {
                return 38;
            }
            break;
        case 'y':
            if (STATIC_TABLE[59].name.equals(name))    // vary
            {
                return 59;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 5
    private static int staticIndex5(DirectBuffer name)
    {
        switch (name.getByte(4))
        {
        case 'e':
            if (STATIC_TABLE[50].name.equals(name))            // range
            {
                return 50;
            }
            break;
        case 'h':
            if (STATIC_TABLE[4].name.equals(name))            // path
            {
                return 4;
            }
            break;
        case 'w':
            if (STATIC_TABLE[22].name.equals(name))           // allow
            {
                return 22;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 6
    private static int staticIndex6(DirectBuffer name)
    {
        switch (name.getByte(5))
        {
        case 'e':
            if (STATIC_TABLE[32].name.equals(name))        // cookie
            {
                return 32;
            }
            break;
        case 'r':
            if (STATIC_TABLE[54].name.equals(name))       // server
            {
                return 54;
            }
            break;
        case 't':
            if (STATIC_TABLE[19].name.equals(name))       // accept
            {
                return 19;
            }
            if (STATIC_TABLE[35].name.equals(name))        // expect
            {
                return 35;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 7
    private static int staticIndex7(DirectBuffer name)
    {
        switch (name.getByte(6))
        {
        case 'd':
            if (STATIC_TABLE[2].name.equals(name))                  // :method
            {
                return 2;
            }
            break;
        case 'e':
            if (STATIC_TABLE[6].name.equals(name))                  // :scheme
            {
                return 6;
            }
            break;
        case 'h':
            if (STATIC_TABLE[52].name.equals(name))                  // refresh
            {
                return 52;
            }
            break;
        case 'r':
            if (STATIC_TABLE[51].name.equals(name))                 // referer
            {
                return 51;
            }
            break;
        case 's':
            if (STATIC_TABLE[8].name.equals(name))                  // :status
            {
                return 8;
            }
            if (STATIC_TABLE[36].name.equals(name))                  // expires
            {
                return 36;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 8
    private static int staticIndex8(DirectBuffer name)
    {
        switch (name.getByte(7))
        {
        case 'e':
            if (STATIC_TABLE[42].name.equals(name))                     // if-range
            {
                return 42;
            }
            break;
        case 'h':
            if (STATIC_TABLE[39].name.equals(name))                     // if-match
            {
                return 39;
            }
            break;
        case 'n':
            if (STATIC_TABLE[46].name.equals(name))                     // location
            {
                return 46;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 10
    private static int staticIndex10(DirectBuffer name)
    {
        switch (name.getByte(9))
        {
        case 'e':
            if (STATIC_TABLE[55].name.equals(name))           // set-cookie
            {
                return 55;
            }
            break;
        case 't':
            if (STATIC_TABLE[58].name.equals(name))           // user-agent
            {
                return 58;
            }
            break;
        case 'y':
            if (STATIC_TABLE[1].name.equals(name))           // :authority
            {
                return 1;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 11
    private static int staticIndex11(DirectBuffer name)
    {
        return (name.getByte(10) == 'r' && STATIC_TABLE[53].name.equals(name)) ? 53 : -1;   // retry-after
    }

    // Index in static table for the given name of length 12
    private static int staticIndex12(DirectBuffer name)
    {
        switch (name.getByte(11))
        {
        case 'e':
            if (STATIC_TABLE[31].name.equals(name))            // content-type
            {
                return 31;
            }
            break;
        case 's':
            if (STATIC_TABLE[47].name.equals(name))            // max-forwards
            {
                return 47;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 13
    private static int staticIndex13(DirectBuffer name)
    {
        switch (name.getByte(12))
        {
        case 'd':
            if (STATIC_TABLE[44].name.equals(name))           // last-modified
            {
                return 44;
            }
            break;
        case 'e':
            if (STATIC_TABLE[30].name.equals(name))           // content-range
            {
                return 30;
            }
            break;
        case 'h':
            if (STATIC_TABLE[41].name.equals(name))           // if-none-match
            {
                return 41;
            }
            break;
        case 'l':
            if (STATIC_TABLE[24].name.equals(name))           // cache-control
            {
                return 24;
            }
            break;
        case 'n':
            if (STATIC_TABLE[23].name.equals(name))           // authorization
            {
                return 23;
            }
            break;
        case 's':
            if (STATIC_TABLE[18].name.equals(name))           // accept-ranges
            {
                return 18;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 14
    private static int staticIndex14(DirectBuffer name)
    {
        switch (name.getByte(13))
        {
        case 'h':
            if (STATIC_TABLE[28].name.equals(name))          // content-length
            {
                return 28;
            }
            break;
        case 't':
            if (STATIC_TABLE[15].name.equals(name))          // accept-charset
            {
                return 15;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 15
    private static int staticIndex15(DirectBuffer name)
    {
        switch (name.getByte(14))
        {
        case 'e':
            if (STATIC_TABLE[17].name.equals(name))         // accept-language
            {
                return 17;
            }
            break;
        case 'g':
            if (STATIC_TABLE[16].name.equals(name))         // accept-encoding
            {
                return 16;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 16
    private static int staticIndex16(DirectBuffer name)
    {
        switch (name.getByte(15))
        {
        case 'e':
            if (STATIC_TABLE[27].name.equals(name))        // content-language
            {
                return 27;
            }
            if (STATIC_TABLE[61].name.equals(name))        // www-authenticate
            {
                return 61;
            }
            break;
        case 'g':
            if (STATIC_TABLE[26].name.equals(name))        // content-encoding
            {
                return 26;
            }
            break;
        case 'n':
            if (STATIC_TABLE[29].name.equals(name))        // content-location
            {
                return 29;
            }
        }
        return -1;
    }

    // Index in static table for the given name of length 17
    private static int staticIndex17(DirectBuffer name)
    {
        switch (name.getByte(16))
        {
        case 'e':
            if (STATIC_TABLE[40].name.equals(name))    // if-modified-since
            {
                return 40;
            }
            break;
        case 'g':
            if (STATIC_TABLE[57].name.equals(name))    // transfer-encoding
            {
                return 57;
            }
            break;
        }
        return -1;
    }

    // Index in static table for the given name of length 18
    private static int staticIndex18(DirectBuffer name)
    {
        return (name.getByte(17) == 'e' && STATIC_TABLE[48].name.equals(name)) ? 48 : -1;   // proxy-authenticate
    }

    // Index in static table for the given name of length 19
    private static int staticIndex19(DirectBuffer name)
    {
        switch (name.getByte(18))
        {
        case 'e':
            if (STATIC_TABLE[43].name.equals(name))    // if-unmodified-since
            {
                return 43;
            }
            break;
        case 'n':
            if (STATIC_TABLE[25].name.equals(name))    // content-disposition
            {
                return 25;
            }
            if (STATIC_TABLE[49].name.equals(name))    // proxy-authorization
            {
                return 49;
            }
        }
        return -1;
    }

    // Index in static table for the given name of length 25
    private static int staticIndex25(DirectBuffer name)
    {
        return (name.getByte(24) == 'y' && STATIC_TABLE[56].name.equals(name)) ? 56 : -1;   // strict-transport-security
    }

    // Index in static table for the given name of length 27
    private static int staticIndex27(DirectBuffer name)
    {
        return (name.getByte(26) == 'n' && STATIC_TABLE[20].name.equals(name)) ? 20 : -1;   // access-control-allow-origi
    }

}
