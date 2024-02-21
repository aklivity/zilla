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
package io.aklivity.zilla.runtime.exporter.stdout.internal.stream;

import java.io.PrintStream;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.exporter.stdout.internal.types.StringFW;

public abstract class EventHandler
{
    protected final LongFunction<String> supplyNamespace;
    protected final LongFunction<String> supplyLocalName;
    protected final PrintStream out;

    public EventHandler(
        LongFunction<String> supplyNamespace,
        LongFunction<String> supplyLocalName,
        PrintStream out)
    {
        this.supplyNamespace = supplyNamespace;
        this.supplyLocalName = supplyLocalName;
        this.out = out;
    }

    public abstract void handleEvent(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length);

    protected static String asString(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null ? "" : s;
    }

    protected static String identity(
        StringFW stringFW)
    {
        String s = stringFW.asString();
        return s == null || s.isEmpty() ? "-" : s;
    }
}
