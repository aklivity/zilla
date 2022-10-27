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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "dump", description = "Dump stream content")
public final class ZillaDumpCommand extends ZillaCommand
{
    @Option(name = {"-t", "--type"},
        description = "streams* | streams-[nowait|zero|head|tail]")
    public String type = "streams";

    @Option(name = {"-v", "--verbose"},
        description = "Show verbose output")
    public boolean verbose;

    @Option(name = {"--version"})
    public boolean version = false;

    @Option(name = {"-f", "--frameTypes"},
        description = "Dump specific frame types only, e.g BEGIN")
    public List<String> frameTypes = new ArrayList<>();

    @Option(name = {"-d", "--directory"},
        description = "Configuration directory")
    public URI directory;

    @Option(name = {"-p", "--pcap"},
        description = "PCAP file location to dump stream")
    public URI pcapLocation;

    @Option(name = {"-i", "--interval"},
        description = "Run command continuously at interval")
    public int interval = 0;

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

        Runnable command = null;
        final Matcher matcher = Pattern.compile("streams(?:-(?<option>[a-z]+))?").matcher(type);
        if (matcher.matches())
        {
            final String option = matcher.group("option");
            final boolean continuous = !"nowait".equals(option);
            final RingBufferSpy.SpyPosition position = continuous && option != null ?
                RingBufferSpy.SpyPosition.valueOf(option.toUpperCase()) :
                RingBufferSpy.SpyPosition.ZERO;


            final Predicate<String> hasFrameTypes = frameTypes.isEmpty() ? t -> true : frameTypes::contains;
            command = new DumpStreamsCommand(config, hasFrameTypes, verbose, continuous, affinity, position,
                pcapLocation.getPath());
        }
        do
        {
            command.run();
            try
            {
                Thread.sleep(TimeUnit.SECONDS.toMillis(interval));
            }
            catch (InterruptedException e)
            {
                //TODO: implement proper logging
                System.out.println("Thread.sleep is interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        } while (interval > 0);
    }
}
