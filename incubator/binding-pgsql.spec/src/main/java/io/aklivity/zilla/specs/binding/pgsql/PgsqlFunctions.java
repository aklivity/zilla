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

import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlQueryDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlRowDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlType;
unctions
{
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
            dataExRW.kind(PgsqlType.QUERY.value());

            return new PgsqlQueryDataExBuilder();
        }

        public PgsqlRowDataExBuilder row()
        {
            dataExRW.kind(PgsqlType.ROW.value());

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

            public PgsqlDataExBuilder build()
            {
                final PgsqlRowDataExFW pgsqlRowDataEx = pgsqlRowDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, pgsqlRowDataEx.limit());
                return PgsqlDataExBuilder.this;
            }
        }
    }

    private PgsqlFunctions()
    {
        // utility
    }
}
