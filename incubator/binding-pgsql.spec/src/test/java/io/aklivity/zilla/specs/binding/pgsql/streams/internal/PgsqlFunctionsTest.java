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
package io.aklivity.zilla.specs.binding.pgsql.streams.internal;

import static io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions.dataEx;
import static io.aklivity.zilla.specs.binding.pgsql.PgsqlFunctions.flushEx;

import org.junit.Test;


public class PgsqlFunctionsTest
{
    @Test
    public void shouldEncodePgsqlDataQueryExtension()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .query()
                .build()
            .build();
    }

    @Test
    public void shouldEncodePgsqlFlushTypeExtension()
    {
        final byte[] array = flushEx()
                              .typeId(0)
                              .type()
                                .column()
                                    .name("balance")
                                    .tableOid(0)
                                    .index((short) 0)
                                    .typeOid(701)
                                    .length((short)8)
                                    .modifier(-1)
                                    .format("TEXT")
                                    .build()
                                .column()
                                    .name("timestamp")
                                    .tableOid(0)
                                    .index((short) 0)
                                    .typeOid(20)
                                    .length((short) 8)
                                    .modifier(-1)
                                    .format("TEXT")
                                    .build()
                                .column()
                                    .name("user_id")
                                    .tableOid(0)
                                    .index((short) 0)
                                    .typeOid(17)
                                    .length((short) -1)
                                    .modifier(-1)
                                    .format("TEXT")
                                    .build()
                                .build()
                              .build();
    }

}
