# shellcheck shell=sh
# Shared readiness/retry helpers for the example smoke tests run by the
# "testing" job in .github/workflows/build.yml.
#
# Why this exists: `docker compose up -d --wait` only blocks on container
# healthchecks. Every example's Zilla healthcheck is a TCP-port probe
# (`echo -n '' > /dev/tcp/...`), which confirms the listener is bound but not
# that the data plane is ready -- the Kafka topic may not yet hold the produced
# record, a consumer group may not have joined, or a route may still be warming
# up. Asserting on the first call therefore races, which is the dominant cause
# of intermittent failures (empty OUTPUT, `OUTPUT=[]` before a record is
# fetchable, or a `timeout` firing before a streamed response arrives).
#
# These helpers gate WHEN an assertion runs without changing WHAT it asserts:
# the data-plane call is re-run until it produces the expected result or a
# bounded number of attempts elapse. The existing comparison block is left
# unchanged and remains the authority on pass/fail.
#
# Source it from an example's .github/test.sh, resolving the path relative to
# the script rather than the working directory so it also works when the script
# is run by hand from the example directory:
#
#   . "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

# retry_until <attempts> <delay_seconds> <command...>
#
# Run <command> repeatedly until it exits 0, up to <attempts> times, sleeping
# <delay_seconds> between attempts. Returns the command's final exit status, so
# the caller can fall through to its normal assertion on the last result.
#
# The first retry is immediate; the delay applies from the second retry onward.
# What these gates wait out is a data-plane race that has usually already
# resolved by the time the first call returns -- the call itself takes long
# enough to be the settle time. Sleeping before re-calling therefore bills
# <delay_seconds> for a condition that is already true, on every gate that needs
# exactly one retry. Retrying once immediately costs a single extra call and
# keeps the full backoff for the cases that are genuinely still warming up.
#
# <command> is typically a shell function defined by the caller that performs
# the call and assigns OUTPUT / RESULT. Because the function runs in the current
# shell -- not a subshell -- the variables it sets remain visible afterwards, so
# the example's existing `echo OUTPUT` / comparison block runs unchanged once
# the gate succeeds or the attempts are exhausted.
retry_until() {
  _attempts=$1
  _delay=$2
  shift 2

  _attempt=1
  until "$@"
  do
    _status=$?
    if [ "$_attempt" -ge "$_attempts" ]
    then
      return "$_status"
    fi
    if [ "$_attempt" -gt 1 ]
    then
      sleep "$_delay"
    fi
    _attempt=$((_attempt + 1))
  done
}

# fail <message>
#
# Record an assertion failure: echo it inline where it happened, remember it for
# report_failures, and set EXIT so the caller's closing `exit $EXIT` reports
# non-zero. A drop-in replacement for an `echo "❌ ..."` + `EXIT=1` pair.
#
# Why this exists: an example that checks many assertions sets EXIT=1 and keeps
# going, so the last thing printed before `exit $EXIT` is whichever assertion
# ran last -- routinely a ✅ -- while the ❌ that actually failed the job sits
# thousands of lines earlier. A failed run then reads like a passing one, and on
# a long log the failing assertion can be past the reach of the log-tail APIs
# altogether, leaving nothing to diagnose from but the exit code.
FAILURES=""
# When FAIL_FAST is 1, the first failure stops the run instead of letting the
# remaining assertions execute. That matters when the diagnosis is frame-level:
# `zilla dump` reads what the engine directory still holds, so the frames worth
# looking at have to be the most recent ones. Traffic after the first failure
# buries them. Off by default, so every other example behaves exactly as before.
FAIL_FAST=${FAIL_FAST:-0}

fail() {
  _message="$*"

  echo "❌ $_message"
  FAILURES="$FAILURES$_message
"
  EXIT=1

  if [ "$FAIL_FAST" = "1" ]
  then
    echo "FAIL_FAST=1, stopping at the first failure so the engine directory still holds its frames"
    report_failures
    exit 1
  fi
}

# report_failures
#
# Re-echo every failure recorded by fail, as the last thing the script prints.
# Call it immediately before the closing `exit $EXIT`. Prints nothing when
# nothing failed, so a passing run is unchanged. Under GitHub Actions each
# failure is also emitted as a workflow error annotation, surfacing it on the
# checks UI without opening the log at all.
report_failures() {
  if [ -n "$FAILURES" ]
  then
    echo "=== failed assertions ==="
    echo "$FAILURES" | while IFS= read -r _failure
    do
      if [ -n "$_failure" ]
      then
        echo "❌ $_failure"
        if [ -n "$GITHUB_ACTIONS" ]
        then
          echo "::error::$_failure"
        fi
      fi
    done
  fi
}
