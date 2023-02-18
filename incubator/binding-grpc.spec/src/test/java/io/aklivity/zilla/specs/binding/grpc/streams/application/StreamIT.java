package io.aklivity.zilla.specs.binding.grpc.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class StreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/grpc/internal/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/unary.rpc/client",
        "${app}/unary.rpc/server",
    })
    public void unaryRequestResponseRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.stream.rpc/client",
        "${app}/client.stream.rpc/server",
    })
    public void clientStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.stream.rpc/client",
        "${app}/server.stream.rpc/server",
    })
    public void serverStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/bidirectional.stream.rpc/client",
        "${app}/bidirectional.stream.rpc/server",
    })
    public void bidirectionalStreamRpc() throws Exception
    {
        k3po.finish();
    }
}
