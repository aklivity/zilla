/*
 * Copyright 2021-2022 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;

import java.net.URI;
import java.util.Properties;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"--version"})
    public boolean version = false;

    @Option(name = {"-d", "--directory"},
        description = "Configuration directory")
    public URI directory;

    @Option(name = {"-o", "--output"},
        description = "PCAP file location to dump stream")
    public URI pcapLocation;

    @Option(name = {"-c"},
        description = "Exit after receiving count packets.")
    public int count = -1;

    @Option(name = {"-a", "--affinity"},
        description = "Affinity mask")
    public long affinity = 0xffff_ffff_ffff_ffffL;

    private final Logger out = System.out::printf;

    @Override
    public void run()
    {
        if (version)
        {
            out.printf("version: %s\n", DumpCommand.class.getPackage().getSpecificationVersion());
        }
        Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), directory.getPath());

        final EngineConfiguration config = new EngineConfiguration(new Configuration(), properties);

        final RingBufferSpy.SpyPosition position = RingBufferSpy.SpyPosition.ZERO;


        Runnable command = new DumpStreamsCommand(config, verbose, count, affinity, position,
            pcapLocation.getPath());
        command.run();
    }
}
