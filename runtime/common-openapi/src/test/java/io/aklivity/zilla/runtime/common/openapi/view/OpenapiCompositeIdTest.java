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
package io.aklivity.zilla.runtime.common.openapi.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OpenapiCompositeIdTest
{
    @Test
    public void shouldRoundTripWithDefaultServerIndex()
    {
        long compositeId = OpenapiCompositeId.compositeId(7, 42);

        assertEquals(7, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(42, OpenapiCompositeId.operationId(compositeId));
        assertEquals(0, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripWithServerIndex()
    {
        long compositeId = OpenapiCompositeId.compositeId(7, 42, 3);

        assertEquals(7, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(42, OpenapiCompositeId.operationId(compositeId));
        assertEquals(3, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtSpecIndexBoundary()
    {
        long compositeId = OpenapiCompositeId.compositeId(0xffff, 0, 0);

        assertEquals(0xffff, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(0, OpenapiCompositeId.operationId(compositeId));
        assertEquals(0, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtOperationIdBoundary()
    {
        long compositeId = OpenapiCompositeId.compositeId(0, 0xffff_ffff, 0);

        assertEquals(0, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(0xffff_ffff, OpenapiCompositeId.operationId(compositeId));
        assertEquals(0, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAtServerIndexBoundary()
    {
        long compositeId = OpenapiCompositeId.compositeId(0, 0, 0xffff);

        assertEquals(0, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(0, OpenapiCompositeId.operationId(compositeId));
        assertEquals(0xffff, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldRoundTripAllFieldsAtMaximumSimultaneously()
    {
        long compositeId = OpenapiCompositeId.compositeId(0xffff, 0xffff_ffff, 0xffff);

        assertEquals(0xffff, OpenapiCompositeId.specIndex(compositeId));
        assertEquals(0xffff_ffff, OpenapiCompositeId.operationId(compositeId));
        assertEquals(0xffff, OpenapiCompositeId.serverIndex(compositeId));
    }

    @Test
    public void shouldNotBleedBetweenFields()
    {
        long specIndexOnly = OpenapiCompositeId.compositeId(0xffff, 0, 0);
        long operationOnly = OpenapiCompositeId.compositeId(0, 0xffff_ffff, 0);
        long serverOnly = OpenapiCompositeId.compositeId(0, 0, 0xffff);

        assertEquals(0, OpenapiCompositeId.operationId(specIndexOnly));
        assertEquals(0, OpenapiCompositeId.serverIndex(specIndexOnly));
        assertEquals(0, OpenapiCompositeId.specIndex(operationOnly));
        assertEquals(0, OpenapiCompositeId.serverIndex(operationOnly));
        assertEquals(0, OpenapiCompositeId.specIndex(serverOnly));
        assertEquals(0, OpenapiCompositeId.operationId(serverOnly));
    }
}
