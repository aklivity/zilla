package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AsyncapiProtocol
{
    protected static final String INLINE_CATALOG_NAME = "catalog0";
    protected static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");

    protected final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");

    protected Asyncapi asyncApi;
    public final String scheme;
    public final String secureScheme;

    protected AsyncapiProtocol(
        Asyncapi asyncApi,
        String scheme,
        String secureScheme)
    {
        this.asyncApi = asyncApi;
        this.scheme = scheme;
        this.secureScheme = secureScheme;
    }

    public abstract <C>BindingConfigBuilder<C> injectProtocolServerOptions(
        BindingConfigBuilder<C> binding);

    public abstract <C>BindingConfigBuilder<C> injectProtocolClientOptions(
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
