/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.engine.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.MutableBoolean;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.engine.internal.types.OctetsFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.TestDataExFW;
import io.aklivity.zilla.specs.engine.internal.types.stream.TestEnvelopeValueFW;

public final class TestFunctions
{
    @Function
    public static TestDataExBuilder dataEx()
    {
        return new TestDataExBuilder();
    }

    @Function
    public static TestDataExMatcherBuilder matchDataEx()
    {
        return new TestDataExMatcherBuilder();
    }

    public static final class TestDataExBuilder
    {
        private final TestDataExFW.Builder dataExRW;
        private final DirectBufferEx valueRO = new UnsafeBufferEx();

        private TestDataExBuilder()
        {
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024 * 8]);
            this.dataExRW = new TestDataExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public TestDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public TestDataExBuilder value(
            String name,
            String value)
        {
            return valueBytes(name, value != null ? value.getBytes(UTF_8) : null);
        }

        public TestDataExBuilder valueBytes(
            String name,
            byte[] value)
        {
            if (value == null)
            {
                dataExRW.valuesItem(v -> v.name(name).length(-1).value((OctetsFW) null));
            }
            else
            {
                valueRO.wrap(value);
                dataExRW.valuesItem(v -> v.name(name)
                                          .length(valueRO.capacity())
                                          .value(valueRO, 0, valueRO.capacity()));
            }
            return this;
        }

        public byte[] build()
        {
            final TestDataExFW dataEx = dataExRW.build();
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
    }

    public static final class TestDataExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final TestDataExFW dataExRO = new TestDataExFW();
        private final List<TestEnvelopeValueMatch> values = new ArrayList<>();

        private Integer typeId;

        public TestDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public TestDataExMatcherBuilder value(
            String name,
            String value)
        {
            return valueBytes(name, value != null ? value.getBytes(UTF_8) : null);
        }

        public TestDataExMatcherBuilder valueBytes(
            String name,
            byte[] value)
        {
            values.add(new TestEnvelopeValueMatch(name, value));
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private TestDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final TestDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.limit());

            if (dataEx != null && matchTypeId(dataEx) && matchValues(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(String.valueOf(dataEx));
        }

        private boolean matchTypeId(
            TestDataExFW dataEx)
        {
            return typeId == dataEx.typeId();
        }

        private boolean matchValues(
            TestDataExFW dataEx)
        {
            return values.stream().allMatch(expected -> containsValue(dataEx, expected));
        }

        private boolean containsValue(
            TestDataExFW dataEx,
            TestEnvelopeValueMatch expected)
        {
            final MutableBoolean found = new MutableBoolean();
            dataEx.values().forEach(actual ->
            {
                if (!found.value && matches(actual, expected))
                {
                    found.value = true;
                }
            });
            return found.value;
        }

        private boolean matches(
            TestEnvelopeValueFW actual,
            TestEnvelopeValueMatch expected)
        {
            final boolean nameMatches = expected.name.equals(actual.name().asString());
            final boolean valueMatches = expected.value == null
                ? actual.value() == null
                : matchesValue(actual.value(), expected.value);
            return nameMatches && valueMatches;
        }

        private boolean matchesValue(
            OctetsFW actual,
            byte[] expected)
        {
            boolean matches = actual != null && actual.sizeof() == expected.length;
            if (matches)
            {
                for (int i = 0; i < expected.length; i++)
                {
                    if (actual.buffer().getByte(actual.offset() + i) != expected[i])
                    {
                        matches = false;
                        break;
                    }
                }
            }
            return matches;
        }
    }

    private static final class TestEnvelopeValueMatch
    {
        private final String name;
        private final byte[] value;

        private TestEnvelopeValueMatch(
            String name,
            byte[] value)
        {
            this.name = name;
            this.value = value;
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(TestFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "test";
        }
    }

    private TestFunctions()
    {
        // utility
    }
}
