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

import org.junit.Test;


public class PgsqlFunctionsTest
{
    @Test
    public void shouldEncodePgsqlDataExtensionWithDeferred()
    {
        final byte[] array = dataEx()
            .typeId(0)
            .query()
                .build()
            .build();
    }

}
