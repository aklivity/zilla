/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.llm.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.llm.internal.types.stream.LlmBeginExFW;

public class LlmFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new LlmFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateBeginEx()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateBeginExWithoutOptionalFields()
    {
        byte[] bytes = LlmFunctions.beginEx()
            .typeId(0)
            .dialect("test-permissive")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchBeginEx() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("test-permissive")
            .contentType("application/vnd.zilla.test-permissive+json")
            .model("gpt-x")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailMatchBeginExMismatch() throws Exception
    {
        BytesMatcher matcher = LlmFunctions.matchBeginEx()
            .typeId(0)
            .dialect("test-strict")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new LlmBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .dialect("test-permissive")
            .build();

        assertThrows(Exception.class, () -> matcher.match(byteBuf));
    }
}
