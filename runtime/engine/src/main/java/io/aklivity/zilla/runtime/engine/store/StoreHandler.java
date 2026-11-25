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
package io.aklivity.zilla.runtime.engine.store;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Provides key-value state operations for streams passing through a binding backed by a store.
 * <p>
 * A {@code StoreHandler} is obtained from {@link StoreContext#attach(StoreConfig)} and is
 * confined to a single I/O thread. It maintains mutable runtime state — session tokens, JWKS
 * keys, idempotency records, rate-limit counters, OAuth nonces — and evaluates operations on
 * the hot path without blocking or holding the calling thread.
 * </p>
 * <p>
 * <b>All operations complete asynchronously.</b> Each method takes a completion callback. The
 * callback fires <em>strictly later</em> than the call returns — never synchronously, even
 * when the underlying backend can compute the result inline. The callback fires on the
 * caller's I/O thread; the implementation owns the responsibility for thread alignment. For a
 * local backend this means deferring via the engine signaler to the next event-loop tick of
 * the same worker; for a networked backend it means hopping the network-completion event
 * back onto the calling worker's signaler before invoking the callback. In either case the
 * caller observes "callback runs on my thread, later".
 * </p>
 * <p>
 * The result of an operation is only valid inside the callback.
 * </p>
 *
 * @see StoreContext
 */
public interface StoreHandler
{
    /**
     * Whether the expiry of an entry is observable through {@link #expire}.
     * <p>
     * {@link #TRACKED}: the expiry is reported to matching {@link #expire} registrations as
     * specified there (the default). {@link #UNTRACKED}: the entry lives for its time-to-live
     * only; its expiry is never reported to any {@link #expire} registration and the store retains
     * no value, index or other state for it beyond what it needs to enforce the time-to-live.
     * </p>
     * <p>
     * The mode belongs to the expiry that a write sets and lives exactly as long as that expiry.
     * A write that does not name a mode is {@link #TRACKED}. An entry written without a
     * time-to-live has no expiry and so no mode.
     * </p>
     */
    enum Expiration
    {
        TRACKED,
        UNTRACKED
    }

    /**
     * Retrieves the value associated with the given key.
     *
     * @param key        the key to look up
     * @param completion a callback that receives {@code (key, value)};
     *                   {@code value} is {@code null} if the key is not present
     */
    void get(
        String key,
        BiConsumer<String, String> completion);

    /**
     * Associates the given value with the given key, replacing any existing value.
     * <p>
     * The expiry is {@link Expiration#TRACKED}, as for {@link #put(String, String, Duration,
     * Expiration, Consumer)}.
     * </p>
     *
     * @param key        the key to store
     * @param value      the value to associate
     * @param ttl        the time-to-live, or {@code null} for no expiry
     * @param completion a callback invoked when the operation completes
     */
    default void put(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        put(key, value, ttl, Expiration.TRACKED, completion);
    }

    /**
     * Associates the given value with the given key, replacing any existing value, and states
     * whether the expiry it sets is reported through {@link #expire}.
     * <p>
     * A write over an entry replaces the mode along with the expiry: a {@link Expiration#TRACKED}
     * write over an {@link Expiration#UNTRACKED} entry reports the new expiry from then on, and an
     * {@link Expiration#UNTRACKED} write over a live {@link Expiration#TRACKED} entry cancels its
     * tracking, so the replaced expiry is never reported, like that of any replaced entry. An
     * entry that has already expired but is not yet swept still reports its expiry before it is
     * overwritten, if it was {@link Expiration#TRACKED}; an expired {@link Expiration#UNTRACKED}
     * entry has nothing to report.
     * </p>
     *
     * @param key        the key to store
     * @param value      the value to associate
     * @param ttl        the time-to-live, or {@code null} for no expiry
     * @param expiration whether the expiry of the stored value is reported through {@link #expire};
     *                   it has no effect when {@code ttl} is {@code null}; never {@code null}
     * @param completion a callback invoked when the operation completes
     */
    void put(
        String key,
        String value,
        Duration ttl,
        Expiration expiration,
        Consumer<String> completion);

    /**
     * Associates the given value with the given key only if the key is not already present.
     * <p>
     * The expiry is {@link Expiration#TRACKED}, as for {@link #putIfAbsent(String, String,
     * Duration, Expiration, Consumer)}.
     * </p>
     *
     * @param key        the key to store
     * @param value      the value to associate if absent
     * @param ttl        the time-to-live, or {@code null} for no expiry
     * @param completion a callback that receives the existing value if present,
     *                   or {@code null} if the key was absent and the value was stored
     */
    default void putIfAbsent(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        putIfAbsent(key, value, ttl, Expiration.TRACKED, completion);
    }

    /**
     * Associates the given value with the given key only if the key is not already present, and
     * states whether the expiry it sets is reported through {@link #expire}.
     * <p>
     * The mode applies only when the value is stored, and follows the rules stated for
     * {@link #put(String, String, Duration, Expiration, Consumer)}.
     * </p>
     *
     * @param key        the key to store
     * @param value      the value to associate if absent
     * @param ttl        the time-to-live, or {@code null} for no expiry
     * @param expiration whether the expiry of the stored value is reported through {@link #expire};
     *                   it has no effect when {@code ttl} is {@code null}; never {@code null}
     * @param completion a callback that receives the existing value if present,
     *                   or {@code null} if the key was absent and the value was stored
     */
    void putIfAbsent(
        String key,
        String value,
        Duration ttl,
        Expiration expiration,
        Consumer<String> completion);

    /**
     * Removes the entry for the given key.
     *
     * @param key        the key to remove
     * @param completion a callback invoked when the operation completes
     */
    void delete(
        String key,
        Consumer<String> completion);

    /**
     * Atomically adds {@code delta} to the counter stored under the given key.
     * <p>
     * An absent or expired key is created at zero before the addition, with {@code ttl} applied
     * on creation only; an existing entry keeps its remaining lifetime. The expiry of a counter
     * created by this call is {@link Expiration#UNTRACKED}, and incrementing an existing entry makes
     * its remaining expiry {@link Expiration#UNTRACKED} too, so a counter is never reported through
     * {@link #expire}: it still expires and is no longer readable, but its expiry is not delivered.
     * The stored form is the
     * decimal string of the counter, so {@link #get} observes the same value. The existing value
     * must be a decimal integer, otherwise the call fails with {@link NumberFormatException}.
     * </p>
     *
     * @param key        the key of the counter
     * @param delta      the amount to add, which may be negative
     * @param ttl        the time-to-live applied when the key is created, or {@code null} for no expiry
     * @param completion a callback that receives {@code (key, value)} where {@code value} is the
     *                   counter after the addition
     */
    void increment(
        String key,
        long delta,
        Duration ttl,
        BiConsumer<String, Long> completion);

    /**
     * Retrieves the values associated with the given keys.
     *
     * @param keys       the keys to look up
     * @param completion a callback that receives the values in key order;
     *                   an element is {@code null} if its key is not present
     */
    void get(
        List<String> keys,
        Consumer<List<String>> completion);

    /**
     * Removes the entries for the given keys.
     * <p>
     * Removal is best-effort and not atomic across keys.
     * </p>
     *
     * @param keys       the keys to remove
     * @param completion a callback that receives the number of keys that were present and removed
     */
    void delete(
        List<String> keys,
        LongConsumer completion);

    /**
     * Atomically replaces the value associated with the given key, only if its current value
     * equals {@code expected}.
     * <p>
     * A caller that loses to a concurrent writer is told so by the completion and may re-read
     * with {@link #get} and try again; it is never blocked on a lock.
     * </p>
     *
     * <p>
     * The expiry is {@link Expiration#TRACKED}, as for {@link #replace(String, String, String,
     * Duration, Expiration, Consumer)}.
     * </p>
     *
     * @param key        the key to update
     * @param expected   the value the key must currently hold
     * @param value      the value to associate if the current value matches
     * @param ttl        the time-to-live of the new value, or {@code null} for no expiry
     * @param completion a callback that receives {@code true} if the value was replaced,
     *                   or {@code false} if the key is absent or holds a different value
     */
    default void replace(
        String key,
        String expected,
        String value,
        Duration ttl,
        Consumer<Boolean> completion)
    {
        replace(key, expected, value, ttl, Expiration.TRACKED, completion);
    }

    /**
     * Atomically replaces the value associated with the given key, only if its current value
     * equals {@code expected}, and states whether the expiry it sets is reported through
     * {@link #expire}.
     * <p>
     * The mode applies only when the value is replaced, and follows the rules stated for
     * {@link #put(String, String, Duration, Expiration, Consumer)}.
     * </p>
     *
     * @param key        the key to update
     * @param expected   the value the key must currently hold
     * @param value      the value to associate if the current value matches
     * @param ttl        the time-to-live of the new value, or {@code null} for no expiry
     * @param expiration whether the expiry of the new value is reported through {@link #expire};
     *                   it has no effect when {@code ttl} is {@code null}; never {@code null}
     * @param completion a callback that receives {@code true} if the value was replaced,
     *                   or {@code false} if the key is absent or holds a different value
     */
    void replace(
        String key,
        String expected,
        String value,
        Duration ttl,
        Expiration expiration,
        Consumer<Boolean> completion);

    /**
     * Restarts the time-to-live of the entry stored under the given key without rewriting its value.
     * <p>
     * The entry expires {@code ttl} after the call, replacing its previous expiry instant whether
     * that was earlier or later. The value is unchanged. An entry that was written without a
     * time-to-live is given one, consistent with {@link #put}, where {@code null} means no expiry:
     * a {@code null} {@code ttl} leaves the entry with no expiry, so that it never expires.
     * Lock leases are not entries and are unaffected.
     * </p>
     * <p>
     * The countdown restarts with the {@link Expiration} mode of the expiry it replaces, so touching
     * an {@link Expiration#UNTRACKED} entry leaves it {@link Expiration#UNTRACKED}. An entry that had
     * no expiry has no mode, and a touch that gives it one makes it {@link Expiration#TRACKED}.
     * </p>
     * <p>
     * A touch is not a write for any other purpose: it does not notify {@link #watch}
     * registrations, because the value did not change.
     * </p>
     * <p>
     * A touch composes with {@link #expire}. The countdown restarts, so no expiry is reported for
     * the previous expiry instant, and the store keeps any retained copy of the value and any
     * index it owns in step with the new expiry instant. An entry found already expired but not
     * yet swept reports its expiry first, as a write does, and the touch then completes as not
     * found.
     * </p>
     * <p>
     * The completion fires <em>strictly later</em> than the call returns, on the caller's I/O
     * thread, following the same async contract as the other {@code StoreHandler} operations.
     * </p>
     *
     * @param key        the key of the entry to touch
     * @param ttl        the time-to-live counted from now, or {@code null} for no expiry
     * @param completion a callback that receives {@code true} if the entry was present and
     *                   unexpired and its time-to-live was restarted, or {@code false} if the
     *                   key is absent or expired, in which case nothing changed
     */
    void touch(
        String key,
        Duration ttl,
        Consumer<Boolean> completion);

    /**
     * Atomically retrieves and removes the entry for the given key.
     *
     * @param key        the key to retrieve and remove
     * @param completion a callback that receives the value that was removed,
     *                   or {@code null} if the key was not present
     */
    void getAndDelete(
        String key,
        Consumer<String> completion);

    /**
     * Attempts to acquire a TTL-bounded lock on the given key without waiting.
     * <p>
     * The completion fires <em>strictly later</em> than the call returns, on the caller's I/O
     * thread, following the same async contract as the other {@code StoreHandler} operations.
     * On success it receives a non-null ownership token, generated by the implementation, that
     * the caller must supply to {@link #unlock} to release. On contention — another holder owns
     * an unexpired lock — the completion receives {@code null}.
     * </p>
     * <p>
     * The lock auto-releases when its TTL elapses; an {@code unlock} from the original holder
     * against an expired or reacquired lock is treated as a no-op.
     * </p>
     *
     * @param key         the key to lock
     * @param ttl         the lease duration, or {@code null} for no expiry
     * @param completion  a callback that receives {@code (key, token)};
     *                    {@code token} is non-null when the lock was acquired,
     *                    {@code null} when another holder owns the key
     */
    void lock(
        String key,
        Duration ttl,
        BiConsumer<String, String> completion);

    /**
     * Releases a lock previously acquired via {@link #lock}, provided the supplied token matches
     * the current holder.
     * <p>
     * On a successful ownership-checked release, the completion receives the supplied token —
     * mirroring the {@link #lock} completion shape (non-null = you own it). On failure — the
     * supplied token does not match the current holder, or the lock has already expired or been
     * released — the completion receives {@code null}. No information about another holder is
     * surfaced to a caller that did not prove ownership.
     * </p>
     * <p>
     * The completion fires <em>strictly later</em> than the call returns, on the caller's I/O
     * thread, following the same async contract as the other {@code StoreHandler} operations.
     * </p>
     *
     * @param key         the key to unlock
     * @param token       the ownership token returned by a prior {@link #lock} call
     * @param completion  a callback that receives the supplied {@code token} on a successful
     *                    ownership-checked release, or {@code null} otherwise
     */
    void unlock(
        String key,
        String token,
        Consumer<String> completion);

    /**
     * Extends the TTL of a lock previously acquired via {@link #lock}, provided the supplied token
     * matches the current holder.
     * <p>
     * Used by callers that hold a lock longer than its initial TTL — for example, a singleton
     * worker that owns a coordination lease for the lifetime of its binding. The caller schedules
     * renewals at an interval shorter than the lease TTL; if any renewal fails the caller has
     * lost ownership and must surrender any externally-visible state predicated on holding the
     * lock.
     * </p>
     * <p>
     * On a successful ownership-checked renewal, the completion receives the supplied token and
     * the lock's expiry is reset to {@code now + ttl}. On failure — the token does not match the
     * current holder, or the lock has already expired and possibly been reacquired by another
     * holder — the completion receives {@code null}. As with {@link #unlock}, the implementation
     * does not surface information about another holder to a caller that did not prove ownership.
     * </p>
     * <p>
     * The completion fires <em>strictly later</em> than the call returns, on the caller's I/O
     * thread, following the same async contract as the other {@code StoreHandler} operations.
     * </p>
     *
     * @param key         the key whose lock is being renewed
     * @param token       the ownership token returned by a prior {@link #lock} call
     * @param ttl         the new lease duration counted from now, or {@code null} for no expiry
     * @param completion  a callback that receives the supplied {@code token} on a successful
     *                    ownership-checked renewal, or {@code null} otherwise
     */
    void renew(
        String key,
        String token,
        Duration ttl,
        Consumer<String> completion);

    /**
     * Registers a listener invoked when the value associated with the given key changes.
     * <p>
     * Registration returns synchronously. The listener fires <em>strictly later</em> than the
     * mutation that triggered it, on the registering caller's I/O thread, following the same
     * async contract as the other {@code StoreHandler} operations. Each invocation receives the
     * key and the new value, or {@code null} when the entry has been removed.
     * </p>
     * <p>
     * Implementations decide the scope of change visibility — in-process, node-wide, or
     * cluster-wide — and document it in their module. Expiry of an entry is not reported
     * through this listener, whatever its {@link Expiration} mode; an expiry is observable only
     * through {@link #expire}, and then only if {@link Expiration#TRACKED}. Explicit removal of an
     * entry is reported, by {@link #delete} and {@link #getAndDelete}.
     * </p>
     *
     * @param key       the key to observe
     * @param listener  a callback that receives {@code (key, value)} on every change;
     *                  {@code value} is {@code null} when the entry has been removed
     * @return a {@link Closeable} handle that unsubscribes the listener when closed
     */
    Closeable watch(
        String key,
        BiConsumer<String, String> listener);

    /**
     * Registers a listener invoked when an entry whose key starts with the given prefix expires.
     * <p>
     * An entry expires when the time-to-live supplied on the write that stored its current value
     * elapses without the entry having been replaced or removed. An entry replaced, removed or
     * incremented before its expiry instant is never reported: an increment leaves the entry its
     * remaining lifetime but ends its tracking. A replacement that carries a new
     * time-to-live restarts the countdown. An entry written without a time-to-live never expires.
     * Lock leases are not entries and are not reported.
     * </p>
     * <p>
     * Only entries written as {@link Expiration#TRACKED} are reported. An {@link Expiration#UNTRACKED}
     * entry still expires and is no longer readable, but its expiry is never delivered to any
     * registration, including one attached after the expiry instant, and the guarantees below do
     * not apply to it.
     * </p>
     * <p>
     * Registration returns synchronously. The listener fires <em>strictly later</em> than the
     * expiry, on the registering caller's I/O thread, following the same async contract as the
     * other {@code StoreHandler} operations. Each invocation receives the key and the value the
     * entry held when it expired. Both are non-null.
     * </p>
     * <p>
     * An implementation guarantees:
     * </p>
     * <ul>
     *   <li><b>Never early.</b> No notification is delivered before the expiry instant.</li>
     *   <li><b>At least once.</b> Every expiry of an entry covered by a registration is delivered
     *       to that registration at least once. A notification that is dropped or fails in transit
     *       is redelivered, and a store whose scope spans processes may deliver the same expiry to
     *       more than one registrant. A registrant makes its reaction idempotent, for example by
     *       guarding it with {@link #putIfAbsent}.</li>
     *   <li><b>Bounded delay.</b> Delivery follows the expiry instant by no more than an
     *       implementation-defined interval, which the implementation documents.</li>
     *   <li><b>Self-contained.</b> The store itself ensures an expiry is not lost because a
     *       registrant attached after the expiry instant, or because the registrant or a transport
     *       was unavailable. Callers are not required to enumerate or re-read state to find
     *       expiries. The implementation documents how long it retains an undelivered expiry.</li>
     *   <li><b>Value retained.</b> A store whose backend discards the value when an entry expires
     *       retains it itself until the expiry has been delivered.</li>
     * </ul>
     * <p>
     * Expiry is distinct from change observed through {@link #watch}, which reports explicit writes
     * and removals only. Ordering between expiries of different keys is unspecified; for one key,
     * the expiry follows any {@link #watch} notification for the write that stored the expired
     * value.
     * </p>
     *
     * @param prefix    the key prefix to observe, matched literally and case-sensitively;
     *                  the empty string matches every key
     * @param listener  a callback that receives {@code (key, value)} on every expiry
     * @return a {@link Closeable} handle that unregisters the listener when closed;
     *         no invocation begins after it is closed on the registering thread
     */
    Closeable expire(
        String prefix,
        BiConsumer<String, String> listener);
}
