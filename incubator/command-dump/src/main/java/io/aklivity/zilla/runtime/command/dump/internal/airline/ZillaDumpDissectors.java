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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.aklivity.zilla.runtime.command.dump.ZillaDumpDissectorSpi;

public final class ZillaDumpDissectors
{
    public static final String TEMPLATE = "zilla.lua";

    private static final String MARKER = "-- @dissectors@\n";

    public static String assemble()
    {
        String template = read(ZillaDumpDissectors.class.getResource(TEMPLATE));
        String dissectors = StreamSupport.stream(load().spliterator(), false)
            .sorted(Comparator.comparing(ZillaDumpDissectorSpi::type))
            .map(ZillaDumpDissectorSpi::dissector)
            .map(ZillaDumpDissectors::read)
            .collect(Collectors.joining());
        return template.replace(MARKER, dissectors);
    }

    private static ServiceLoader<ZillaDumpDissectorSpi> load()
    {
        return ServiceLoader.load(ZillaDumpDissectorSpi.class, ZillaDumpDissectors.class.getClassLoader());
    }

    private static String read(
        URL resource)
    {
        String text;
        try (InputStream in = resource.openStream())
        {
            text = new String(in.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return text;
    }

    private ZillaDumpDissectors()
    {
    }
}
