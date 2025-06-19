package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.agrona.concurrent.AgentRunner.startOnThread;

import java.nio.channels.SelectableChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.agrona.DeadlineTimerWheel;
import org.agrona.ErrorHandler;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.AgentTerminationException;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.internal.poller.Poller;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public class EngineBoss implements EngineController, Agent
{
    private static final String AGENT_NAME = "EngineBoss";

    private final AgentRunner runner;
    private final Supplier<IdleStrategy> supplyIdleStrategy;

    private final DeadlineTimerWheel timerWheel;
    private final Poller poller;
    private final int expireLimit;
    private final DeadlineTimerWheel.TimerHandler expireHandler;
    private final Long2ObjectHashMap<Runnable> tasksByTimerId;

    private volatile Thread thread;

    public EngineBoss(
        EngineConfiguration config,
        ErrorHandler errorHandler)
    {
        this.poller = new Poller();
        this.timerWheel = new DeadlineTimerWheel(MILLISECONDS, currentTimeMillis(), 512, 1024);
        this.expireLimit = config.maximumExpirationsPerPoll();
        this.expireHandler = this::handleExpire;
        this.tasksByTimerId = new Long2ObjectHashMap<>();

        this.supplyIdleStrategy = () -> new BackoffIdleStrategy(
            config.maxSpins(),
            config.maxYields(),
            config.minParkNanos(),
            config.maxParkNanos());

        this.runner = new AgentRunner(supplyIdleStrategy.get(), errorHandler, null, this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workDone = 0;

        try
        {
            workDone += poller.doWork();

            if (timerWheel.timerCount() != 0L)
            {
                final long now = currentTimeMillis();
                int expiredMax = expireLimit;
                while (timerWheel.currentTickTime() <= now && expiredMax > 0)
                {
                    final int expired = timerWheel.poll(now, expireHandler, expiredMax);

                    workDone += expired;
                    expiredMax -= expired;
                }
            }
        }
        catch (Throwable ex)
        {
            throw new AgentTerminationException(ex);
        }

        return workDone;
    }

    public void doStart()
    {
        thread = startOnThread(runner, Thread::new);
    }

    public void doClose()
    {
        try
        {
            Consumer<Thread> timeout = t -> rethrowUnchecked(new IllegalStateException("close timeout"));
            runner.close((int) SECONDS.toMillis(5L), timeout);
        }
        finally
        {
            thread = null;
        }
    }

    @Override
    public String roleName()
    {
        return AGENT_NAME;
    }

    public PollerKey supplyPollerKey(
        SelectableChannel channel)
    {
        return poller.register(channel);
    }

    private boolean handleExpire(
        TimeUnit timeUnit,
        long now,
        long timerId)
    {
        final Runnable task = tasksByTimerId.remove(timerId);
        if (task != null)
        {
            task.run();
        }
        return true;
    }
}
