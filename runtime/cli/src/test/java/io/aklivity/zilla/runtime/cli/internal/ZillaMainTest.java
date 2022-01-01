/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cli.internal;

import static io.aklivity.zilla.runtime.cli.internal.ZillaTestCommandSpi.TEST_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ZillaMainTest
{
    @Test
    public void shouldInvokeTestCommandWithDefaultArgument()
    {
        int status = ZillaMain.invoke(new String[] { "test" });

        assertEquals(0, status);
        assertEquals("arg", TEST_ARGUMENT.get());
    }

    @Test
    public void shouldInvokeTestCommandWithOverriddenArgument()
    {
        int status = ZillaMain.invoke(new String[] { "test", "arg1" });

        assertEquals(0, status);
        assertEquals("arg1", TEST_ARGUMENT.get());
    }

    @Test
    public void shouldInvokeTestCommandThatThrowsException()
    {
        int status = ZillaMain.invoke(new String[] { "test", "throw" });

        assertEquals(1, status);
    }
}
