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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;

public class AsyncapiCompositeBindingAdapter
{
    protected static final String APPLICATION_JSON = "application/json";

    protected Asyncapi asyncApi;
    protected boolean isTlsEnabled;
    protected int[] allPorts;
    protected int[] compositePorts;
    protected AsyncapiProtocol protocol;
    protected String qname;
    protected String qvault;


    protected AsyncapiProtocol resolveProtocol(
        String protocol,
        AsyncapiOptionsConfig options)
    {
        Pattern pattern = Pattern.compile("(http|mqtt)");
        Matcher matcher = pattern.matcher(protocol);
        AsyncapiProtocol asyncapiProtocol = null;
        if (matcher.find())
        {
            switch (matcher.group())
            {
            case "http":
                asyncapiProtocol = new AsyncapiHttpProtocol(qname, asyncApi, options);
                break;
            case "mqtt":
                asyncapiProtocol = new AyncapiMqttProtocol(qname, asyncApi);
                break;
            case "kafka":
            case "kafka-secure":
                asyncapiProtocol = new AyncapiKafkaProtocol(qname, asyncApi, options, protocol);
                break;
            }
        }
        else
        {
            // TODO: should we do something?
        }
        return asyncapiProtocol;
    }
}
