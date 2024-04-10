package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.Objects.requireNonNull;

import java.net.URI;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiServer;

public final class OpenapiView
{
    private final Openapi openapi;

    public int[] resolvePortsForScheme(
        String scheme)
    {
        requireNonNull(scheme);
        int[] ports = null;
        URI url = findFirstServerUrlWithScheme(scheme);
        if (url != null)
        {
            ports = new int[] {url.getPort()};
        }
        return ports;
    }

    public URI findFirstServerUrlWithScheme(
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (OpenapiServer server : openapi.servers)
        {
            OpenapiServerView view = OpenapiServerView.of(server);
            if (scheme.equals(view.url().getScheme()))
            {
                result = view.url();
                break;
            }
        }
        return result;
    }

    public static OpenapiView of(
        Openapi openapi)
    {
        return new OpenapiView(openapi);
    }

    private OpenapiView(
        Openapi openapi)
    {
        this.openapi = openapi;
    }
}
