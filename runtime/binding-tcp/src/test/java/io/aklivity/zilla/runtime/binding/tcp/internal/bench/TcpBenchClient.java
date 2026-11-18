/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

import static java.net.StandardSocketOptions.SO_LINGER;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Ramps rounds of connections against a {@link TcpBenchServer}, each connection completing one echo round
 * trip, and reports per round the rate and the connect-to-echo latency. All threads start a round together
 * and hold their connections until every thread has finished the round; the connections are then closed
 * and the next round starts after a pause, so the server can report the distribution at the peak.
 * <p>
 * Arguments: {@code host ports threads connections rounds [batch] [holdMillis] [pauseMillis] [abortive] [sources]
 * [rate]}. {@code ports} is
 * a single port or a range such as {@code 12345-12348}, spread across the threads. {@code connections} is the
 * total per round, divided evenly between the threads. Connections are held for {@code holdMillis} after a
 * round so the server can sample them, and the next round starts {@code pauseMillis} after they are closed.
 * With {@code abortive} the connections are closed with a reset, which leaves no TIME_WAIT behind on the
 * client. {@code sources} is a comma-separated list of local addresses; connections are bound to them in turn,
 * which multiplies the source ports available for one server address and port. With a {@code rate}, connections
 * are started on a fixed schedule of that many per second across all threads, whether or not earlier ones have
 * completed, instead of in synchronized batches; the round reports the offered rate and how far the threads fell
 * behind the schedule. Each thread polls every in-flight connection on every pass, so a paced thread falls
 * behind its schedule once thousands of connections are in flight at once. The first round is a warmup and is
 * left out of the summary.
 */
public final class TcpBenchClient
{
    private static final int PAYLOAD = 64;
    private static final long TIMEOUT_NANOS = SECONDS.toNanos(120L);

    public static void main(
        String[] args) throws Exception
    {
        final String host = args[0];
        final int[] ports = parsePorts(args[1]);
        final int threads = Integer.parseInt(args[2]);
        final int connections = Integer.parseInt(args[3]);
        final int rounds = Integer.parseInt(args[4]);
        final int batch = args.length > 5 ? Integer.parseInt(args[5]) : 256;
        final long holdMillis = args.length > 6 ? Long.parseLong(args[6]) : 2_000L;
        final long pauseMillis = args.length > 7 ? Long.parseLong(args[7]) : 2_500L;
        final boolean abortive = args.length > 8 && Boolean.parseBoolean(args[8]);
        final InetAddress[] sources = parseSources(args.length > 9 ? args[9] : "");
        final long rate = args.length > 10 ? Long.parseLong(args[10]) : 0L;

        final int share = connections / threads;

        System.out.printf("client config host=%s ports=%s threads=%d connections=%d rounds=%d batch=%d " +
            "abortive=%b sources=%d rate=%d%n",
            host, args[1], threads, share * threads, rounds, batch, abortive, Math.max(1, sources.length), rate);

        final Reporter reporter = new Reporter();
        final CyclicBarrier start = new CyclicBarrier(threads);
        final CyclicBarrier end = new CyclicBarrier(threads, reporter::report);

        final List<Thread> workers = new ArrayList<>();
        for (int index = 0; index < threads; index++)
        {
            final int port = ports[0] + index % (ports[1] - ports[0] + 1);
            final InetSocketAddress destination = new InetSocketAddress(host, port);
            final Ramper ramper = new Ramper(destination, share, batch, rounds, holdMillis, pauseMillis, abortive,
                sources, index, rate / threads, start, end);
            reporter.rampers.add(ramper);

            final Thread thread = new Thread(ramper, "ramper-" + index);
            workers.add(thread);
            thread.start();
        }

        for (Thread thread : workers)
        {
            thread.join();
        }

        reporter.summarize();
        System.exit(0);
    }

    private static InetAddress[] parseSources(
        String text) throws IOException
    {
        final String[] parts = text.isBlank() ? new String[0] : text.split(",");
        final InetAddress[] sources = new InetAddress[parts.length];

        for (int index = 0; index < parts.length; index++)
        {
            sources[index] = InetAddress.getByName(parts[index].trim());
        }

        return sources;
    }

    private static int[] parsePorts(
        String text)
    {
        final String[] parts = text.split("-");

        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[parts.length - 1]) };
    }

    private static final class Reporter
    {
        private final List<Ramper> rampers = new ArrayList<>();

        private int round;
        private int measured;
        private double rateSum;
        private double p99Sum;

        void report()
        {
            round++;

            int established = 0;
            int failed = 0;
            long elapsedNanos = 0L;
            long startNanos = 0L;
            long lagNanos = 0L;
            for (Ramper ramper : rampers)
            {
                established += ramper.established;
                failed += ramper.failed;
                elapsedNanos = Math.max(elapsedNanos, ramper.elapsedNanos);
                startNanos = Math.max(startNanos, ramper.startElapsedNanos);
                lagNanos = Math.max(lagNanos, ramper.lagNanos);
            }

            final long[] latencies = new long[established];
            int offset = 0;
            for (Ramper ramper : rampers)
            {
                System.arraycopy(ramper.latencies, 0, latencies, offset, ramper.established);
                offset += ramper.established;
            }
            Arrays.sort(latencies);

            final double seconds = elapsedNanos / 1_000_000_000.0;
            final double rate = seconds > 0.0 ? established / seconds : 0.0;
            final double p50 = established > 0 ? latencies[established / 2] / 1_000_000.0 : 0.0;
            final double p99 = established > 0 ? latencies[Math.min(established - 1, established * 99 / 100)] / 1_000_000.0 : 0.0;
            final double max = established > 0 ? latencies[established - 1] / 1_000_000.0 : 0.0;

            final String paced = startNanos > 0L ?
                " offered=%.0f lagMs=%d".formatted((established + failed) / (startNanos / 1_000_000_000.0),
                    lagNanos / 1_000_000L) : "";

            System.out.printf("client round=%d%s established=%d failed=%d elapsedMs=%d rate=%.0f conn/s latency(ms) " +
                "p50=%.1f p99=%.1f max=%.1f%s%n",
                round, round == 1 ? " (warmup)" : "", established, failed, elapsedNanos / 1_000_000L, rate, p50, p99, max,
                paced);

            if (round > 1)
            {
                measured++;
                rateSum += rate;
                p99Sum += p99;
            }
        }

        void summarize()
        {
            System.out.printf("client summary measuredRounds=%d meanRate=%.0f conn/s meanP99(ms)=%.1f%n",
                measured, measured > 0 ? rateSum / measured : 0.0, measured > 0 ? p99Sum / measured : 0.0);
        }
    }

    private static final class Ramper implements Runnable
    {
        private static final int CONNECTING = 0;
        private static final int ECHOING = 1;
        private static final int ECHOED = 2;

        private final InetSocketAddress destination;
        private final int share;
        private final int rounds;
        private final long holdMillis;
        private final long pauseMillis;
        private final boolean abortive;
        private final InetAddress[] sources;
        private final int sourceOffset;
        private final long ratePerThread;
        private final int batchSize;
        private final CyclicBarrier start;
        private final CyclicBarrier end;
        private final SocketChannel[] batch;
        private final int[] stages;
        private final int[] echoed;
        private final long[] starts;
        private final List<SocketChannel> held = new ArrayList<>();
        private final ByteBuffer request = allocateDirect(PAYLOAD).order(nativeOrder());
        private final ByteBuffer reply = allocateDirect(PAYLOAD).order(nativeOrder());

        private long[] latencies;
        private int next;
        private int established;
        private int failed;
        private long elapsedNanos;
        private long startElapsedNanos;
        private long lagNanos;

        private Ramper(
            InetSocketAddress destination,
            int share,
            int batch,
            int rounds,
            long holdMillis,
            long pauseMillis,
            boolean abortive,
            InetAddress[] sources,
            int sourceOffset,
            long ratePerThread,
            CyclicBarrier start,
            CyclicBarrier end)
        {
            this.destination = destination;
            this.share = share;
            this.rounds = rounds;
            this.holdMillis = holdMillis;
            this.pauseMillis = pauseMillis;
            this.abortive = abortive;
            this.sources = sources;
            this.sourceOffset = sourceOffset;
            this.ratePerThread = ratePerThread;
            this.batchSize = batch;
            this.start = start;
            this.end = end;

            final int capacity = ratePerThread > 0L ? share : batch;
            this.batch = new SocketChannel[capacity];
            this.stages = new int[capacity];
            this.echoed = new int[capacity];
            this.starts = new long[capacity];
            this.latencies = new long[share];
            this.request.put(new byte[PAYLOAD]);
        }

        @Override
        public void run()
        {
            try
            {
                for (int round = 1; round <= rounds; round++)
                {
                    Thread.sleep(round == 1 ? 0L : pauseMillis);
                    start.await();
                    ramp();
                    end.await();
                    Thread.sleep(holdMillis);
                    closeHeld();
                }
            }
            catch (IOException | InterruptedException | BrokenBarrierException ex)
            {
                ex.printStackTrace();
                System.exit(1);
            }
        }

        private void ramp() throws IOException
        {
            final long begin = System.nanoTime();
            final long deadline = begin + TIMEOUT_NANOS;

            established = 0;
            failed = 0;

            if (ratePerThread > 0L)
            {
                rampPaced(deadline);
            }
            else
            {
                for (int opened = 0; opened < share; opened += batchSize)
                {
                    rampBatch(Math.min(batchSize, share - opened), deadline);
                }
            }

            elapsedNanos = System.nanoTime() - begin;
        }

        private void open(
            int index) throws IOException
        {
            final SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.setOption(TCP_NODELAY, true);

            if (sources.length > 0)
            {
                channel.bind(new InetSocketAddress(sources[(sourceOffset + next++) % sources.length], 0));
            }

            batch[index] = channel;
            stages[index] = CONNECTING;
            echoed[index] = 0;
            starts[index] = System.nanoTime();

            channel.connect(destination);
        }

        private void rampPaced(
            long deadline) throws IOException
        {
            final long intervalNanos = 1_000_000_000L / ratePerThread;
            final long begin = System.nanoTime();

            long nextStart = begin;
            int started = 0;
            int inflight = 0;

            lagNanos = 0L;
            startElapsedNanos = 0L;

            while (established + failed < share)
            {
                final long now = System.nanoTime();
                int work = 0;

                while (started < share && now >= nextStart)
                {
                    lagNanos = Math.max(lagNanos, now - nextStart);
                    open(inflight++);
                    started++;
                    nextStart += intervalNanos;
                    work++;

                    if (started == share)
                    {
                        startElapsedNanos = now - begin;
                    }
                }

                int retained = 0;
                for (int index = 0; index < inflight; index++)
                {
                    work += advance(index);

                    if (stages[index] != ECHOED)
                    {
                        retain(index, retained++);
                    }
                }
                inflight = retained;

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IOException("ramp timed out");
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private void retain(
            int from,
            int to)
        {
            if (from != to)
            {
                batch[to] = batch[from];
                stages[to] = stages[from];
                echoed[to] = echoed[from];
                starts[to] = starts[from];
            }
        }

        private void rampBatch(
            int size,
            long deadline) throws IOException
        {
            for (int index = 0; index < size; index++)
            {
                open(index);
            }

            int pending = size;
            while (pending > 0)
            {
                int work = 0;

                for (int index = 0; index < size; index++)
                {
                    if (stages[index] != ECHOED)
                    {
                        work += advance(index);
                        pending -= stages[index] == ECHOED ? 1 : 0;
                    }
                }

                if (work == 0)
                {
                    if (System.nanoTime() > deadline)
                    {
                        throw new IOException("ramp timed out");
                    }

                    Thread.onSpinWait();
                }
            }
        }

        private int advance(
            int index)
        {
            int work = 0;
            final SocketChannel channel = batch[index];

            try
            {
                if (stages[index] == CONNECTING && channel.finishConnect())
                {
                    request.clear();
                    channel.write(request);
                    stages[index] = ECHOING;
                    work = 1;
                }

                if (stages[index] == ECHOING)
                {
                    reply.clear();
                    final int bytesRead = channel.read(reply);

                    if (bytesRead < 0)
                    {
                        throw new IOException("closed by server");
                    }

                    echoed[index] += bytesRead;
                    work += bytesRead > 0 ? 1 : 0;

                    if (echoed[index] >= PAYLOAD)
                    {
                        latencies[established++] = System.nanoTime() - starts[index];
                        held.add(channel);
                        stages[index] = ECHOED;
                    }
                }
            }
            catch (IOException ex)
            {
                failed++;
                stages[index] = ECHOED;
                work = 1;
                closeQuietly(channel);
            }

            return work;
        }

        private void closeHeld()
        {
            for (SocketChannel channel : held)
            {
                if (abortive)
                {
                    resetQuietly(channel);
                }

                closeQuietly(channel);
            }

            held.clear();
        }

        private static void resetQuietly(
            SocketChannel channel)
        {
            try
            {
                channel.setOption(SO_LINGER, 0);
            }
            catch (IOException ex)
            {
                // ignore
            }
        }

        private static void closeQuietly(
            SocketChannel channel)
        {
            try
            {
                channel.close();
            }
            catch (IOException ex)
            {
                // ignore
            }
        }
    }

    private TcpBenchClient()
    {
    }
}
