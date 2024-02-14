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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;

public abstract class AsyncapiProtocol
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");

    protected Asyncapi asyncApi;
    protected String qname;
    public final String scheme;
    public final String secureScheme;

    protected AsyncapiProtocol(
        String qname,
        Asyncapi asyncApi,
        String scheme,
        String secureScheme)
    {
        this.qname = qname;
        this.asyncApi = asyncApi;
        this.scheme = scheme;
        this.secureScheme = secureScheme;
    }

    public abstract <C>BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding);

    public abstract <C> BindingConfigBuilder<C> injectProtocolServerRoutes(
        BindingConfigBuilder<C> binding);

    protected <C> CatalogedConfigBuilder<C> injectJsonSchemas(
        CatalogedConfigBuilder<C> cataloged,
        Map<String, AsyncapiMessage> messages,
        String contentType)
    {
        for (Map.Entry<String, AsyncapiMessage> messageEntry : messages.entrySet())
        {
            AsyncapiMessageView message =
                AsyncapiMessageView.of(asyncApi.asyncapiComponents.messages, messageEntry.getValue());
            String schema = messageEntry.getKey();
            if (message.contentType().equals(contentType))
            {
                cataloged
                    .schema()
                    .subject(schema)
                    .build()
                    .build();
            }
            else
            {
                throw new RuntimeException("Invalid content type");
            }
        }
        return cataloged;
    }

    protected boolean hasJsonContentType()
    {
        String contentType = null;
        if (asyncApi.asyncapiComponents != null && asyncApi.asyncapiComponents.messages != null &&
            !asyncApi.asyncapiComponents.messages.isEmpty())
        {
            AsyncapiMessage firstAsyncapiMessage = asyncApi.asyncapiComponents.messages.entrySet().stream()
                .findFirst().get().getValue();
            contentType = AsyncapiMessageView.of(asyncApi.asyncapiComponents.messages, firstAsyncapiMessage).contentType();
        }
        return contentType != null && jsonContentType.reset(contentType).matches();
    }
}
