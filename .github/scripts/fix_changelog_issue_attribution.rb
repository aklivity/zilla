#!/usr/bin/env ruby
# frozen_string_literal: true
#
# Post-processes a CHANGELOG.md already produced by github_changelog_generator.
#
# github_changelog_generator attributes PRs to a release by real git ancestry
# (a PR's merge commit is an ancestor of the earliest tag that contains it) -
# that part is reliable. But it attributes plain *issues* by comparing the
# issue's closed-at date to each tag's date, with no ancestry check at all.
# On a repo with a long-running develop branch, that silently misattributes
# any issue whose actual fix hasn't shipped yet (or shipped in a different
# release) to whatever tag happens to be newest at the time it closed.
#
# This script fixes that: for every issue bullet in the CHANGELOG, it finds
# the issue's real closing commit (via the GitHub REST timeline API), then
# determines the earliest tag that commit is actually an ancestor of (via
# `git tag --contains`) - the same ancestry standard PRs are already held to.
# Issues with no closing commit (closed as stale/wontfix/duplicate, i.e. no
# code shipped) are dropped rather than left implying a resolution that
# never happened. Issues whose lookup fails after retries are left exactly
# where they were, so a flaky network never silently misfiles or drops one.
#
# Usage:
#   ruby fix_changelog_issue_attribution.rb <path-to-CHANGELOG.md> <owner> <repo> [git-repo-path] [branch-ref]
#
# Env:
#   GITHUB_TOKEN                     - required for live API calls
#   CHANGELOG_FIX_MOCK_CLOSING_SHAS  - optional path to a JSON file
#                                      {"issue_number": commit_sha_or_null, ...}
#                                      used instead of live API calls, for
#                                      offline testing/validation.

require 'json'
require 'net/http'
require 'uri'
require 'open3'

CHANGELOG_PATH = ARGV[0] or abort 'usage: fix_changelog_issue_attribution.rb <CHANGELOG.md> <owner> <repo> [git-repo-path] [branch-ref]'
OWNER = ARGV[1] or abort 'owner required'
REPO = ARGV[2] or abort 'repo required'
GIT_REPO_PATH = ARGV[3] || Dir.pwd
BRANCH_REF = ARGV[4] || 'HEAD'

OPEN_TIMEOUT = 10
READ_TIMEOUT = 20
MAX_ATTEMPTS = 4
RETRY_BACKOFF = [2, 5, 10].freeze # seconds, one entry per retry after the first attempt

SUBSECTION_ORDER = [
  '**Implemented enhancements:**',
  '**Fixed bugs:**',
  '**Closed issues:**',
  '**Merged pull requests:**'
].freeze

ISSUE_OR_PR_BULLET = %r{^-\s.*\[\\#(\d+)\]\(https://github\.com/#{Regexp.escape(OWNER)}/#{Regexp.escape(REPO)}/(issues|pull)/\d+\)}.freeze

def git(*args)
  out, status = Open3.capture2('git', '-C', GIT_REPO_PATH, *args)
  [out.force_encoding('UTF-8').scrub, status.success?]
end

def is_ancestor?(sha, ref)
  _, ok = git('merge-base', '--is-ancestor', sha, ref)
  ok
end

# All tags (by name) whose history contains this commit - one git call,
# rather than one `merge-base --is-ancestor` call per candidate tag.
def tags_containing(sha)
  out, _ = git('tag', '--contains', sha)
  out.each_line.map(&:strip).reject(&:empty?).to_set
end

# ---------------------------------------------------------------------------
# Parse CHANGELOG.md into an ordered list of sections.
# ---------------------------------------------------------------------------
HeaderRe = /^## \[([^\]]+)\]\(([^)]+)\)(?:\s*\(([^)]*)\))?/.freeze

def parse_changelog(path)
  lines = File.readlines(path, encoding: 'UTF-8')
  sections = []
  preamble = []
  current = nil
  lines.each do |line|
    if (m = HeaderRe.match(line))
      sections << current if current
      current = { tag: m[1], tree_url: m[2], date: m[3], body: [] }
    elsif current.nil?
      preamble << line
    else
      current[:body] << line
    end
  end
  sections << current if current
  [preamble, sections]
end

def split_body(body)
  subs = SUBSECTION_ORDER.each_with_object({}) { |h, acc| acc[h] = [] }
  current_header = nil
  body.each do |line|
    stripped = line.chomp
    if SUBSECTION_ORDER.include?(stripped)
      current_header = stripped
    elsif stripped.start_with?('[Full Changelog]') || stripped.strip.empty?
      next
    elsif current_header
      subs[current_header] << line
    end
  end
  subs
end

def bullet_number_and_kind(line)
  m = ISSUE_OR_PR_BULLET.match(line)
  return [nil, nil] unless m

  [m[1].to_i, m[2]]
end

# ---------------------------------------------------------------------------
# Fetch each issue's closing commit sha, live or via mock fixture.
# ---------------------------------------------------------------------------
require 'set'

class ClosingCommitFetchError < StandardError; end

# Reused across all requests - avoids a fresh TCP+TLS handshake per issue,
# which is both slow and a bigger surface for transient timeouts.
def http_session
  @http_session ||= begin
    http = Net::HTTP.new('api.github.com', 443)
    http.use_ssl = true
    http.open_timeout = OPEN_TIMEOUT
    http.read_timeout = READ_TIMEOUT
    http.start
    http
  end
end

def fetch_closing_commit_live(issue_number)
  token = ENV.fetch('GITHUB_TOKEN')
  path = "/repos/#{OWNER}/#{REPO}/issues/#{issue_number}/timeline?per_page=100"
  closing_commit = nil
  loop do
    req = Net::HTTP::Get.new(path)
    req['Authorization'] = "Bearer #{token}"
    req['Accept'] = 'application/vnd.github+json'
    req['X-GitHub-Api-Version'] = '2022-11-28'
    res = http_session.request(req)
    raise ClosingCommitFetchError, "GitHub API #{res.code} for issue ##{issue_number}: #{res.body[0, 200]}" unless res.code.to_i == 200

    events = JSON.parse(res.body)
    events.each do |ev|
      closing_commit = ev['commit_id'] if ev['event'] == 'closed' && ev['commit_id']
    end
    link = res['Link']
    next_path = link&.split(',')&.map(&:strip)&.find { |p| p.end_with?('rel="next"') }&.match(/<https:\/\/api\.github\.com([^>]+)>/)&.captures&.first
    break unless next_path

    path = next_path
  end
  closing_commit
rescue Net::OpenTimeout, Net::ReadTimeout, Errno::ETIMEDOUT, Errno::ECONNRESET, SocketError, OpenSSL::SSL::SSLError => e
  @http_session = nil # force a fresh connection on the next attempt
  raise ClosingCommitFetchError, "#{e.class}: #{e.message}"
end

def fetch_closing_commit_with_retries(issue_number)
  attempt = 0
  begin
    attempt += 1
    fetch_closing_commit_live(issue_number)
  rescue ClosingCommitFetchError => e
    if attempt < MAX_ATTEMPTS
      delay = RETRY_BACKOFF[attempt - 1] || RETRY_BACKOFF.last
      warn "    retry #{attempt}/#{MAX_ATTEMPTS - 1} for ##{issue_number} after #{e.message} (waiting #{delay}s)"
      sleep delay
      retry
    end
    warn "    giving up on ##{issue_number} after #{MAX_ATTEMPTS} attempts: #{e.message}"
    :fetch_failed
  end
end

def load_mock_closing_shas
  path = ENV.fetch('CHANGELOG_FIX_MOCK_CLOSING_SHAS')
  JSON.parse(File.read(path))
end

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
preamble, sections = parse_changelog(CHANGELOG_PATH)

mock_closing_shas = ENV['CHANGELOG_FIX_MOCK_CLOSING_SHAS'] ? load_mock_closing_shas : nil

# tag order, oldest -> newest, real-release sections only (Unreleased has no date)
ordered_tags = sections.reverse.reject { |s| s[:tag] == 'Unreleased' }.map { |s| s[:tag] }
tag_index = ordered_tags.each_with_index.to_h

issue_bullets = [] # [{tag:, header:, number:, line:}]
sections_body = {}

sections.each do |s|
  subs = split_body(s[:body])
  sections_body[s[:tag]] = subs
  ['**Implemented enhancements:**', '**Fixed bugs:**', '**Closed issues:**'].each do |header|
    subs[header].each do |line|
      num, kind = bullet_number_and_kind(line)
      next unless kind == 'issues'

      issue_bullets << { tag: s[:tag], header: header, number: num, line: line }
    end
  end
end

total = issue_bullets.size
warn "Parsed #{sections.length} sections, #{total} issue bullets to re-check."

corrections = { moved: [], dropped_no_fix: [], dropped_not_on_branch: [], unchanged: [], skipped_error: [] }

# Remove all issue bullets from their current homes; we'll reinsert them below.
sections_body.each_value do |subs|
  ['**Implemented enhancements:**', '**Fixed bugs:**', '**Closed issues:**'].each do |header|
    subs[header] = subs[header].reject { |line| bullet_number_and_kind(line)[1] == 'issues' }
  end
end

unreleased_subs = sections_body['Unreleased'] || SUBSECTION_ORDER.each_with_object({}) { |h, acc| acc[h] = [] }
had_unreleased = sections.any? { |s| s[:tag] == 'Unreleased' }

issue_bullets.each_with_index do |b, i|
  warn "[#{i + 1}/#{total}] ##{b[:number]} (currently under #{b[:tag]})" if ((i + 1) % 5).zero? || (i + 1) == total

  closing_sha =
    if mock_closing_shas
      mock_closing_shas[b[:number].to_s]
    else
      fetch_closing_commit_with_retries(b[:number])
    end

  if closing_sha == :fetch_failed
    corrections[:skipped_error] << b
    target_subs = b[:tag] == 'Unreleased' ? unreleased_subs : sections_body[b[:tag]]
    target_subs[b[:header]] << b[:line]
    next
  end

  if closing_sha.nil?
    corrections[:dropped_no_fix] << b
    next
  end

  containing = tags_containing(closing_sha) & ordered_tags.to_set
  correct_tag =
    if containing.any?
      containing.min_by { |t| tag_index[t] }
    elsif is_ancestor?(closing_sha, BRANCH_REF)
      'Unreleased'
    end

  if correct_tag.nil?
    corrections[:dropped_not_on_branch] << b.merge(closing_sha: closing_sha)
    next
  end

  target_subs = correct_tag == 'Unreleased' ? unreleased_subs : sections_body[correct_tag]
  target_subs[b[:header]] << b[:line]
  if correct_tag == b[:tag]
    corrections[:unchanged] << b
  else
    corrections[:moved] << b.merge(correct_tag: correct_tag, closing_sha: closing_sha)
  end
end

# sort each subsection's bullets descending by number, per convention
sections_body.each_value do |subs|
  subs.each_key do |h|
    subs[h] = subs[h].sort_by { |line| -bullet_number_and_kind(line)[0].to_i }
  end
end
unreleased_subs.each_key { |h| unreleased_subs[h] = unreleased_subs[h].sort_by { |line| -bullet_number_and_kind(line)[0].to_i } }

# ---------------------------------------------------------------------------
# Render
# ---------------------------------------------------------------------------
def render_section(tag, tree_url, date, subs)
  out = +''
  date_str = date ? " (#{date})" : ''
  out << "## [#{tag}](#{tree_url})#{date_str}\n\n"
  SUBSECTION_ORDER.each do |h|
    next if subs[h].empty?

    out << "#{h}\n\n"
    subs[h].each { |line| out << line }
    out << "\n"
  end
  out
end

output = +''
output << preamble.join

unreleased_nonempty = unreleased_subs.values.any? { |v| !v.empty? }
if unreleased_nonempty
  output << render_section('Unreleased', "https://github.com/#{OWNER}/#{REPO}/tree/HEAD", nil, unreleased_subs)
elsif had_unreleased
  # keep an empty placeholder header, matching convention
  output << "## [Unreleased](https://github.com/#{OWNER}/#{REPO}/tree/HEAD)\n\n"
end

sections.reject { |s| s[:tag] == 'Unreleased' }.each do |s|
  # preserve original compare-link line verbatim if present
  compare_line = s[:body].find { |l| l.start_with?('[Full Changelog]') }&.chomp
  out = render_section(s[:tag], s[:tree_url], s[:date], sections_body[s[:tag]])
  if compare_line
    header_line, rest = out.split("\n", 2)
    out = "#{header_line}\n\n#{compare_line}\n#{rest}"
  end
  output << out
end

output << "\n\\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*\n"

File.write(CHANGELOG_PATH, output)

warn "\n=== Corrections summary ==="
warn "Moved to correct tag: #{corrections[:moved].size}"
corrections[:moved].each { |c| warn "  ##{c[:number]} #{c[:tag]} -> #{c[:correct_tag]} (via #{c[:closing_sha][0, 8]})" }
warn "Dropped (no closing commit - stale/wontfix/duplicate): #{corrections[:dropped_no_fix].size}"
corrections[:dropped_no_fix].each { |c| warn "  ##{c[:number]} (was under #{c[:tag]})" }
warn "Dropped (closing commit not on this branch at all): #{corrections[:dropped_not_on_branch].size}"
corrections[:dropped_not_on_branch].each { |c| warn "  ##{c[:number]} (was under #{c[:tag]}, closing commit #{c[:closing_sha][0, 8]})" }
warn "Left unchanged after lookup failure (network error - not dropped, not moved): #{corrections[:skipped_error].size}"
corrections[:skipped_error].each { |c| warn "  ##{c[:number]} (still under #{c[:tag]})" }
warn "Already correct: #{corrections[:unchanged].size}"
