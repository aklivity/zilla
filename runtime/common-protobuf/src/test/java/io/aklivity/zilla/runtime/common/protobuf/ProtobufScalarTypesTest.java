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

public class ProtobufScalarTypesTest
{
    private final ProtobufSchema schema = newSchema();

    @Test
    public void shouldRoundTripAllScalarTypes()
    {
        String json = "{" +
            "\"fInt32\":-5," +
            "\"fSint32\":-6," +
            "\"fUint32\":4000000000," +
            "\"fFixed32\":4000000001," +
            "\"fSfixed32\":-8," +
            "\"fInt64\":\"9223372036854775807\"," +
            "\"fUint64\":\"18446744073709551615\"," +
            "\"fSint64\":\"-9223372036854775808\"," +
            "\"fFixed64\":\"18446744073709551614\"," +
            "\"fSfixed64\":\"-9\"," +
            "\"fBool\":true," +
            "\"fDouble\":1.5," +
            "\"fFloat\":0.25," +
            "\"fString\":\"hi\"," +
            "\"fBytes\":\"AAEC\"}";
        assertEquals(json, roundTrip(json));
    }

    @Test
    public void shouldRoundTripDoubleNaN()
    {
        String json = "{\"fDouble\":\"NaN\"}";
        assertEquals(json, roundTrip(json));
    }

    @Test
    public void shouldRoundTripDoubleInfinity()
    {
        String json = "{\"fDouble\":\"Infinity\"}";
        assertEquals(json, roundTrip(json));
    }

    @Test
    public void shouldRoundTripFloatNegativeInfinity()
    {
        String json = "{\"fFloat\":\"-Infinity\"}";
        assertEquals(json, roundTrip(json));
    }

    @Test
    public void shouldRoundTripFloatNaN()
    {
        String json = "{\"fFloat\":\"NaN\"}";
        assertEquals(json, roundTrip(json));
    }

    private String roundTrip(
        String json)
    {
        MutableDirectBuffer wire = new UnsafeBuffer(new byte[8192]);
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer jsonBuffer = new UnsafeBuffer(jsonBytes);

        JsonToProtobuf encoder = StreamingProtobuf.jsonToProtobuf(schema);
        int wireLength = encoder.convert("All", jsonBuffer, 0, jsonBytes.length, wire, 0);

        MutableDirectBuffer out = new UnsafeBuffer(new byte[8192]);
        ProtobufToJson decoder = StreamingProtobuf.protobufToJson(schema);
        int jsonLength = decoder.convert("All", wire, 0, wireLength, out, 0);

        return out.getStringWithoutLengthUtf8(0, jsonLength);
    }

    private static ProtobufSchema newSchema()
    {
        return StreamingProtobuf.schema()
            .message(ProtobufMessage.builder("All")
                .field(scalar(1, "f_int32", ProtobufType.INT32))
                .field(scalar(2, "f_sint32", ProtobufType.SINT32))
                .field(scalar(3, "f_uint32", ProtobufType.UINT32))
                .field(scalar(4, "f_fixed32", ProtobufType.FIXED32))
                .field(scalar(5, "f_sfixed32", ProtobufType.SFIXED32))
                .field(scalar(6, "f_int64", ProtobufType.INT64))
                .field(scalar(7, "f_uint64", ProtobufType.UINT64))
                .field(scalar(8, "f_sint64", ProtobufType.SINT64))
                .field(scalar(9, "f_fixed64", ProtobufType.FIXED64))
                .field(scalar(10, "f_sfixed64", ProtobufType.SFIXED64))
                .field(scalar(11, "f_bool", ProtobufType.BOOL))
                .field(scalar(12, "f_double", ProtobufType.DOUBLE))
                .field(scalar(13, "f_float", ProtobufType.FLOAT))
                .field(scalar(14, "f_string", ProtobufType.STRING))
                .field(scalar(15, "f_bytes", ProtobufType.BYTES))
                .build())
            .build();
    }

    private static ProtobufField scalar(
        int number,
        String name,
        ProtobufType type)
    {
        return ProtobufField.builder().number(number).name(name).type(type).build();
    }
}
