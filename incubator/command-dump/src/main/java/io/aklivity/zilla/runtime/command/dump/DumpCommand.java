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
package io.aklivity.zilla.runtime.command.dump;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.apache.commons.cli.Option.builder;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import io.aklivity.zilla.runtime.command.dump.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class DumpCommand
{
    public static void main(
        String[] args) throws Exception
    {
        final CommandLineParser parser = new DefaultParser();

        Options options = new Options();
        options.addOption(builder("h").longOpt("help").desc("print this message").build());
        options.addOption(builder().longOpt("version").build());
        options.addOption(builder("t").hasArg()
            .required(false)
            .longOpt("type")
            .desc("streams* | streams-[nowait|zero|head|tail]")
            .build());
        options.addOption(builder("f").hasArgs()
            .required(false)
            .longOpt("frameTypes")
            .desc("log specific frame types only, e.g BEGIN")
            .build());
        options.addOption(builder("d").longOpt("directory").hasArg().desc("configuration directory").build());
        options.addOption(builder("p").longOpt("pcap").hasArg().desc("pcap file location to dump stream").build());
        options.addOption(builder("v").longOpt("verbose").desc("verbose").build());
        options.addOption(builder("i").hasArg().longOpt("interval").desc("run command continuously at interval").build());
        options.addOption(builder("a").hasArg().longOpt("affinity").desc("affinity mask").build());

        final CommandLine cmdline = parser.parse(options, args);
        final Logger out = System.out::printf;

        final boolean hasVersion = cmdline.hasOption("version");
        final boolean hasDirectory = cmdline.hasOption("directory");
        final boolean hasHelp = cmdline.hasOption("help");

        if (hasVersion)
        {
            out.printf("version: %s\n", DumpCommand.class.getPackage().getSpecificationVersion());
        }

        if (hasHelp || !hasDirectory)
        {
            if (hasHelp || !hasVersion)
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("log", options);
            }
        }
        else
        {
            String directory = cmdline.getOptionValue("directory");
            boolean verbose = cmdline.hasOption("verbose");
            String type = cmdline.getOptionValue("type", "streams");
            final int interval = Integer.parseInt(cmdline.getOptionValue("interval", "0"));
            final long affinity = Long.parseLong(cmdline.getOptionValue("affinity", Long.toString(0xffff_ffff_ffff_ffffL)));
            final String pcapLocation = cmdline.getOptionValue("pcap", "");

            Properties properties = new Properties();
            properties.setProperty(ENGINE_DIRECTORY.name(), directory);

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

                final String[] frameTypes = cmdline.getOptionValues("frameTypes");

                final Predicate<String> hasFrameTypes =
                    frameTypes == null ? t -> true : Arrays.asList(frameTypes)::contains;

                command = new DumpStreamsCommand(config, hasFrameTypes, verbose, continuous, affinity, position, pcapLocation);
            }
            do
            {
                command.run();
                Thread.sleep(TimeUnit.SECONDS.toMillis(interval));
            } while (interval > 0);
        }
    }

    private DumpCommand()
    {
        // utility
    }
}
