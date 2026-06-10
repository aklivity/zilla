/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.protobuf;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class ProtobufWellKnownTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRoundTripTimestamp()
    {
        String json = "{\"ts\":\"2024-01-02T03:04:05Z\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripDuration()
    {
        String json = "{\"dur\":\"1.500s\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripInt32Wrapper()
    {
        String json = "{\"i32\":7}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripStringWrapper()
    {
        String json = "{\"str\":\"hi\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripBoolWrapper()
    {
        String json = "{\"flag\":true}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripStruct()
    {
        String json = "{\"meta\":{\"a\":\"x\",\"b\":true,\"c\":2.5,\"d\":null,\"e\":{\"f\":\"y\"}}}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripListValue()
    {
        String json = "{\"items\":[2.5,\"two\",false,null]}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValue()
    {
        String json = "{\"val\":2.5}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripFieldMask()
    {
        String json = "{\"mask\":\"fooBar,baz\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripEmpty()
    {
        String json = "{\"nothing\":{}}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripTimestampWithNanos()
    {
        String json = "{\"ts\":\"2024-01-02T03:04:05.123456789Z\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripNegativeDuration()
    {
        String json = "{\"dur\":\"-1.500s\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripDurationMicros()
    {
        String json = "{\"dur\":\"0.000500s\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripDurationNanos()
    {
        String json = "{\"dur\":\"3.000000001s\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripDoubleWrapper()
    {
        String json = "{\"dbl\":1.5}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripFloatWrapper()
    {
        String json = "{\"flt\":0.25}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripInt64Wrapper()
    {
        String json = "{\"i64\":\"123\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripUInt64Wrapper()
    {
        String json = "{\"u64\":\"18446744073709551615\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripUInt32Wrapper()
    {
        String json = "{\"u32\":4000000000}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripBytesWrapper()
    {
        String json = "{\"byt\":\"AAEC\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValueAsString()
    {
        String json = "{\"val\":\"text\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValueAsBool()
    {
        String json = "{\"val\":true}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValueAsNull()
    {
        String json = "{\"val\":null}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValueAsObject()
    {
        String json = "{\"val\":{\"k\":\"v\"}}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripValueAsList()
    {
        String json = "{\"val\":[2.5,\"x\"]}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripNestedStruct()
    {
        String json = "{\"meta\":{\"n\":2.5,\"list\":[1.5,\"a\"],\"obj\":{\"x\":true}}}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    @Test
    public void shouldRoundTripFieldMaskMultiplePaths()
    {
        String json = "{\"mask\":\"fooBar,baz.quxQuux\"}";
        assertEquals(json, roundTrip("Wkt", json));
    }

    private String roundTrip(
        String messageName,
        String json)
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[8192]);
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer jsonBuffer = new UnsafeBuffer(jsonBytes);

        JsonToProtobuf encoder = StreamingProtobuf.jsonToProtobuf(schema);
        int wireLength = encoder.convert(messageName, jsonBuffer, 0, jsonBytes.length, wire, 0);

        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufToJson decoder = StreamingProtobuf.protobufToJson(schema);
        int jsonLength = decoder.convert(messageName, wire, 0, wireLength, out, 0);

        return out.getStringWithoutLengthUtf8(0, jsonLength);
    }

    private static ProtobufSchema newSchema()
    {
        return StreamingProtobuf.schema()
            .message(ProtobufMessage.builder("Wkt")
                .field(wkt(1, "ts", "google.protobuf.Timestamp"))
                .field(wkt(2, "dur", "google.protobuf.Duration"))
                .field(wkt(3, "i32", "google.protobuf.Int32Value"))
                .field(wkt(4, "str", "google.protobuf.StringValue"))
                .field(wkt(5, "flag", "google.protobuf.BoolValue"))
                .field(wkt(6, "meta", "google.protobuf.Struct"))
                .field(wkt(7, "items", "google.protobuf.ListValue"))
                .field(wkt(8, "val", "google.protobuf.Value"))
                .field(wkt(9, "mask", "google.protobuf.FieldMask"))
                .field(wkt(10, "nothing", "google.protobuf.Empty"))
                .field(wkt(11, "dbl", "google.protobuf.DoubleValue"))
                .field(wkt(12, "flt", "google.protobuf.FloatValue"))
                .field(wkt(13, "i64", "google.protobuf.Int64Value"))
                .field(wkt(14, "u64", "google.protobuf.UInt64Value"))
                .field(wkt(15, "u32", "google.protobuf.UInt32Value"))
                .field(wkt(16, "byt", "google.protobuf.BytesValue"))
                .build())
            .build();
    }

    private static ProtobufField wkt(
        int number,
        String name,
        String typeName)
    {
        return ProtobufField.builder()
            .number(number)
            .name(name)
            .type(ProtobufType.MESSAGE)
            .typeName(typeName)
            .build();
    }
}
