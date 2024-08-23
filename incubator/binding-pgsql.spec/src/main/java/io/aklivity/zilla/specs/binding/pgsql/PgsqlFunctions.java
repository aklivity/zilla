/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.pgsql;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlColumnInfoFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlCompletedFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlFormat;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlMessageType;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlQueryDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlReadyFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlRowDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlStatus;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlTypeFlushExFW;

public final class PgsqlFunctions
{
    @Function
    public static PgsqlBeginExBuilder beginEx()
    {
        return new PgsqlBeginExBuilder();
    }

    @Function
    public static PgsqlDataExBuilder dataEx()
    {
        return new PgsqlDataExBuilder();
    }

    @Function
    public static PgsqlFlushExBuilder flushEx()
    {
        return new PgsqlFlushExBuilder();
    }

    public static final class PgsqlBeginExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final PgsqlBeginExFW.Builder beginExRW = new PgsqlBeginExFW.Builder();

        private PgsqlBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public PgsqlBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public PgsqlBeginExBuilder parameter(
            String name,
            String value)
        {
            beginExRW.parametersItem(p -> p
                .name(String.format("%s\u0000", name))
                .value(String.format("%s\u0000", value)));

            return this;
        }

        public byte[] build()
        {
            final PgsqlBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }
    }

    public static final class PgsqlDataExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final PgsqlDataExFW dataExRO = new PgsqlDataExFW();

        private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();

        private PgsqlDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public PgsqlDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public PgsqlQueryDataExBuilder query()
        {
            dataExRW.kind(PgsqlMessageType.QUERY.value());

            return new PgsqlQueryDataExBuilder();
        }

        public PgsqlRowDataExBuilder row()
        {
            dataExRW.kind(PgsqlMessageType.ROW.value());

            return new PgsqlRowDataExBuilder();
        }

        public byte[] build()
        {
            final PgsqlDataExFW dataEx = dataExRO;
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }

        public final class PgsqlQueryDataExBuilder
        {
            private final PgsqlQueryDataExFW.Builder pgsqlQueryDataExRW = new PgsqlQueryDataExFW.Builder();

            private PgsqlQueryDataExBuilder()
            {
                pgsqlQueryDataExRW.wrap(writeBuffer, PgsqlDataExFW.FIELD_OFFSET_QUERY, writeBuffer.capacity());
            }

            public PgsqlQueryDataExBuilder deferred(
                int deferred)
            {
                pgsqlQueryDataExRW.deferred(deferred);
                return this;
            }

            public PgsqlDataExBuilder build()
            {
                final PgsqlQueryDataExFW pgsqlQueryDataEx = pgsqlQueryDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, pgsqlQueryDataEx.limit());
                return PgsqlDataExBuilder.this;
            }
        }

        public final class PgsqlRowDataExBuilder
        {
            private final PgsqlRowDataExFW.Builder pgsqlRowDataExRW = new PgsqlRowDataExFW.Builder();

            private PgsqlRowDataExBuilder()
            {
                pgsqlRowDataExRW.wrap(writeBuffer, PgsqlDataExFW.FIELD_OFFSET_QUERY, writeBuffer.capacity());
            }

            public PgsqlRowDataExBuilder deferred(
                int deferred)
            {
                pgsqlRowDataExRW.deferred(deferred);
                return this;
            }

            public PgsqlDataExBuilder build()
            {
                final PgsqlRowDataExFW pgsqlRowDataEx = pgsqlRowDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, pgsqlRowDataEx.limit());
                return PgsqlDataExBuilder.this;
            }
        }
    }

    public static final class PgsqlFlushExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final PgsqlFlushExFW flushExRO = new PgsqlFlushExFW();

        private final PgsqlFlushExFW.Builder flushExRW = new PgsqlFlushExFW.Builder();

        private PgsqlFlushExBuilder()
        {
            flushExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public PgsqlFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public PgsqlTypeFlushExBuilder type()
        {
            flushExRW.kind(PgsqlMessageType.TYPE.value());

            return new PgsqlTypeFlushExBuilder();
        }

        public PgsqlCompletedFlushExBuilder completion()
        {
            flushExRW.kind(PgsqlMessageType.COMPLETION.value());

            return new PgsqlCompletedFlushExBuilder();
        }

        public PgsqlReadyFlushExBuilder ready()
        {
            flushExRW.kind(PgsqlMessageType.READY.value());

            return new PgsqlReadyFlushExBuilder();
        }

        public byte[] build()
        {
            final PgsqlFlushExFW dataEx = flushExRO;
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }

        public final class PgsqlTypeFlushExBuilder
        {
            private final PgsqlTypeFlushExFW.Builder pgsqlTypeFlushExRW = new PgsqlTypeFlushExFW.Builder();

            private PgsqlTypeFlushExBuilder()
            {
                pgsqlTypeFlushExRW.wrap(writeBuffer, PgsqlFlushExFW.FIELD_OFFSET_TYPE, writeBuffer.capacity());
            }

            public PgsqlColumnInfoBuilder column()
            {
                return new PgsqlColumnInfoBuilder();
            }

            public final class PgsqlColumnInfoBuilder
            {
                private final MutableDirectBuffer columnInfoBuffer = new UnsafeBuffer(new byte[1024 * 8]);
                private final PgsqlColumnInfoFW.Builder columnInfoRW = new PgsqlColumnInfoFW.Builder();

                PgsqlColumnInfoBuilder()
                {
                    columnInfoRW.wrap(columnInfoBuffer, 0, columnInfoBuffer.capacity());
                }

                public PgsqlColumnInfoBuilder name(
                    String name)
                {
                    columnInfoRW.name(String.format("%s\u0000", name));
                    return this;
                }

                public PgsqlColumnInfoBuilder tableOid(
                    int tableOid)
                {
                    columnInfoRW.tableOid(tableOid);
                    return this;
                }

                public PgsqlColumnInfoBuilder index(
                    short index)
                {
                    columnInfoRW.index(index);
                    return this;
                }

                public PgsqlColumnInfoBuilder typeOid(
                    int typeOid)
                {
                    columnInfoRW.typeOid(typeOid);
                    return this;
                }

                public PgsqlColumnInfoBuilder length(
                    short length)
                {
                    columnInfoRW.length(length);
                    return this;
                }

                public PgsqlColumnInfoBuilder modifier(
                    int modifier)
                {
                    columnInfoRW.modifier(modifier);
                    return this;
                }

                public PgsqlColumnInfoBuilder format(
                    String format)
                {
                    columnInfoRW.format(f -> f.set(PgsqlFormat.valueOf(format)));
                    return this;
                }

                public PgsqlTypeFlushExBuilder build()
                {
                    PgsqlColumnInfoFW columnInfo = columnInfoRW.build();
                    pgsqlTypeFlushExRW.columnsItem(c -> c
                        .name(columnInfo.name())
                        .tableOid(columnInfo.tableOid())
                        .index(columnInfo.index())
                        .typeOid(columnInfo.typeOid())
                        .length(columnInfo.length())
                        .modifier(columnInfo.modifier())
                        .format(columnInfo.format()));

                    return PgsqlTypeFlushExBuilder.this;
                }
            }

            public PgsqlFlushExBuilder build()
            {
                final PgsqlTypeFlushExFW pgsqlTypeFlushEx = pgsqlTypeFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, pgsqlTypeFlushEx.limit());
                return PgsqlFlushExBuilder.this;
            }
        }

        public final class PgsqlCompletedFlushExBuilder
        {
            private final PgsqlCompletedFlushExFW.Builder pgsqlCompletedFlushExRW = new PgsqlCompletedFlushExFW.Builder();

            private PgsqlCompletedFlushExBuilder()
            {
                pgsqlCompletedFlushExRW.wrap(writeBuffer, PgsqlFlushExFW.FIELD_OFFSET_TYPE, writeBuffer.capacity());
            }

            public PgsqlCompletedFlushExBuilder tag(
                String tag)
            {
                pgsqlCompletedFlushExRW.tag(String.format("%s\u0000", tag));
                return this;
            }

            public PgsqlFlushExBuilder build()
            {
                final PgsqlCompletedFlushExFW pgsqlCompletedFlushEx = pgsqlCompletedFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, pgsqlCompletedFlushEx.limit());
                return PgsqlFlushExBuilder.this;
            }
        }

        public final class PgsqlReadyFlushExBuilder
        {
            private final PgsqlReadyFlushExFW.Builder pgsqlReadyFlushExRW = new PgsqlReadyFlushExFW.Builder();

            private PgsqlReadyFlushExBuilder()
            {
                pgsqlReadyFlushExRW.wrap(writeBuffer, PgsqlFlushExFW.FIELD_OFFSET_TYPE, writeBuffer.capacity());
            }

            public PgsqlReadyFlushExBuilder status(
                String status)
            {
                pgsqlReadyFlushExRW.status(s -> s.set(PgsqlStatus.valueOf(status)));
                return this;
            }

            public PgsqlFlushExBuilder build()
            {
                final PgsqlReadyFlushExFW pgsqlReadyFlushEx = pgsqlReadyFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, pgsqlReadyFlushEx.limit());
                return PgsqlFlushExBuilder.this;
            }
        }
    }

    private PgsqlFunctions()
    {
        // utility
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {

        public Mapper()
        {
            super(PgsqlFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "pgsql";
        }
    }
}
