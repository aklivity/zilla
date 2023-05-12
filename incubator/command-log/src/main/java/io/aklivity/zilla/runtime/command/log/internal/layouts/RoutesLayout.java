/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.log.internal.layouts;

import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class RoutesLayout extends Layout
{
    private final DirectBuffer routesBuffer;

    private RoutesLayout(
        DirectBuffer routesBuffer)
    {
        this.routesBuffer = routesBuffer;
    }

    @Override
    public void close()
    {
        unmap(routesBuffer.byteBuffer());
    }

    public DirectBuffer routesBuffer()
    {
        return routesBuffer;
    }

    public static final class Builder extends Layout.Builder<RoutesLayout>
    {

        private Path path;

        public Builder routesPath(Path path)
        {
            this.path = path;
            return this;
        }

        @Override
        public RoutesLayout build()
        {
            final File routes = path.toFile();

            final MappedByteBuffer mappedRoutes = mapExistingFile(routes, "routes");

            final DirectBuffer routesBuffer = new UnsafeBuffer(mappedRoutes);

            return new RoutesLayout(routesBuffer);
        }
    }
}
