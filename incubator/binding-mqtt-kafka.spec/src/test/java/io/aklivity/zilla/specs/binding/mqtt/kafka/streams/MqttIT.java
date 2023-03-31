package io.aklivity.zilla.specs.binding.mqtt.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class MqttIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mqtt", "io/aklivity/zilla/specs/binding/mqtt/kafka/streams/mqtt");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mqtt}/publish.one.message/client",
        "${mqtt}/publish.one.message/server"})
    public void shouldPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.one.message/client",
        "${mqtt}/subscribe.one.message/server"})
    public void shouldSubscribeOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.no.local/client",
        "${mqtt}/subscribe.no.local/server"})
    public void shouldSubscribeNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.multi.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.multi.level.wildcard/server"})
    public void shouldSubscribeWithWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.single.level.wildcard/server"})
    public void shouldSubscribeToSingleLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.single.and.multi.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.single.and.multi.level.wildcard/server"})
    public void shouldSubscribeToSingleAndMultiLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filter.two.single.level.wildcard/client",
        "${mqtt}/subscribe.topic.filter.two.single.level.wildcard/server"})
    public void shouldSubscribeToTwoSingleLevelWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.both.exact/client",
        "${mqtt}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldSubscribeWithAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.both.exact/client",
        "${mqtt}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldSubscribeWithIsolatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.overlapping.wildcards/client",
        "${mqtt}/subscribe.topic.filters.overlapping.wildcards/server"})
    public void shouldSubscribeToOverlappingWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${mqtt}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldSubscribeWithAggregatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mqtt}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${mqtt}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldSubscribeWithIsolatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }
}
