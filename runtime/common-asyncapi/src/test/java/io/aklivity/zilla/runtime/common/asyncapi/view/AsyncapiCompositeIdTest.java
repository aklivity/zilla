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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AsyncapiCompositeIdTest
{
    @Test
    public void shouldRoundTripWithDefaultServerIndex()
    {
        long compositeId = AsyncapiCompositeId.compositeId(7, 42);

        assertEquals(7, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(42, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(0, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripWithServerIndex()
    {
        long compositeId = AsyncapiCompositeId.compositeId(7, 42, 3);

        assertEquals(7, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(42, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(3, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtSpecIndexBoundary()
    {
        long compositeId = AsyncapiCompositeId.compositeId(0xffff, 0, 0);

        assertEquals(0xffff, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(0, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(0, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtOperationIdBoundary()
    {
        long compositeId = AsyncapiCompositeId.compositeId(0, 0xffff_ffff, 0);

        assertEquals(0, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(0xffff_ffff, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(0, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtServerIndexBoundary()
    {
        long compositeId = AsyncapiCompositeId.compositeId(0, 0, 0xffff);

        assertEquals(0, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(0, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(0xffff, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAllFieldsAtMaximumSimultaneously()
    {
        long compositeId = AsyncapiCompositeId.compositeId(0xffff, 0xffff_ffff, 0xffff);

        assertEquals(0xffff, AsyncapiCompositeId.specIndex(compositeId));
        assertEquals(0xffff_ffff, AsyncapiCompositeId.operationId(compositeId));
        assertEquals(0xffff, AsyncapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldNotBleedBetweenFields()
    {
        long specIndexOnly = AsyncapiCompositeId.compositeId(0xffff, 0, 0);
        long operationOnly = AsyncapiCompositeId.compositeId(0, 0xffff_ffff, 0);
        long serverOnly = AsyncapiCompositeId.compositeId(0, 0, 0xffff);

        assertEquals(0, AsyncapiCompositeId.operationId(specIndexOnly));
        assertEquals(0, AsyncapiCompositeId.serverIndex(specIndexOnly));
        assertEquals(0, AsyncapiCompositeId.specIndex(operationOnly));
        assertEquals(0, AsyncapiCompositeId.serverIndex(operationOnly));
        assertEquals(0, AsyncapiCompositeId.specIndex(serverOnly));
        assertEquals(0, AsyncapiCompositeId.operationId(serverOnly));
    }
}
