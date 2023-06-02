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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;

import java.time.Duration;
import java.util.Properties;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class Hello
{
    public void hello()
    {
        Properties props = new Properties();
        props.put(ENGINE_DIRECTORY.name(), "/Users/attila/az/zilla-run/.zilla/engine");
        EngineConfiguration config = new EngineConfiguration(props);
        OltpExporterHandler handler = new OltpExporterHandler(config, null, null);

        handler.start();
        wait(5);
        handler.stop();
    }

    private static void wait(
        int minutes)
    {
        try
        {
            Thread.sleep(Duration.ofMinutes(minutes).toMillis());
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args)
    {
        Hello hello = new Hello();
        hello.hello();
    }
}
