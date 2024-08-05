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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import static io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfiguration.ASYNCAPI_COMPOSITE_ROUTE_ID;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AsyncapiConfigurationTest
{
    public static final String ASYNCAPI_COMPOSITE_ROUTE_ID_NAME = "zilla.binding.asyncapi.composite.route.id";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(ASYNCAPI_COMPOSITE_ROUTE_ID.name(), ASYNCAPI_COMPOSITE_ROUTE_ID_NAME);
    }
}
