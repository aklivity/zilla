#!/usr/bin/env ruby
# frozen_string_literal: true
#
# Post-processes a CHANGELOG.md already produced by github_changelog_generator.
#
# github_changelog_generator attributes both issues and PRs unreliably once a
# repo has a long-running develop branch plus maintenance branches:
#
# - Issues are bucketed by comparing closed-at date to each tag's date, with
#   no ancestry check at all - silently misattributing any issue whose fix
#   hasn't shipped yet (or shipped on a different branch) to whatever tag is
#   newest when it closes.
# - PRs are *supposed* to be ancestry-based, but the gem's own PR-to-tag
#   association relies on a timeline "merged" event's commit_id rather than
#   the PR object's authoritative merge_commit_sha field (a limitation noted
#   in its own source: "Wish I could use merge_commit_sha, but gcg doesn't
#   currently fetch that"). That's unreliable for squash-merged backport PRs
#   into a non-default branch - which is exactly the maintenance-branch
#   pattern this script exists for - so whole releases can end up with zero
#   "Merged pull requests" entries even though real PRs shipped in them.
#
# This script rebuilds both from scratch, from the same authoritative source:
# for issues, the real closing commit via the GitHub REST timeline API; for
# PRs, the real merge_commit_sha via the GitHub REST pulls API (bulk-fetched,
# no per-PR call needed). Each is then placed by asking git which tag it's
# actually an ancestor of (`git tag --contains`) - the same standard, applied
# consistently to both. Anything with no closing commit (issue closed as
# stale/wontfix/duplicate - no code shipped) or whose commit isn't reachable
# from this branch at all is dropped rather than left implying a resolution
# that never happened here. Anything whose lookup fails after retries is left
# exactly where it was, so a flaky network never silently misfiles or drops
# something.
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
#   CHANGELOG_FIX_MOCK_MERGED_PRS    - optional path to a JSON file, an array
#                                      of {"number":, "title":, "sha":,
#                                      "login":, "bot": true|false} objects,
#                                      used instead of live API calls.

require 'json'
require 'net/http'
require 'uri'
require 'open3'
require 'set'

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

def escape_markdown(text)
  # Leave inline code spans untouched; escape markdown-special characters
  # everywhere else, matching github_changelog_generator's own escaping.
  parts = text.split(/(`[^`]*`)/)
  parts.map.with_index do |part, i|
    if i.even?
      part.gsub(/([\\()\[\]_*#])/) { "\\#{Regexp.last_match(1)}" }
    else
      part
    end
  end.join
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
# HTTP - persistent connection, explicit timeouts, retry with backoff.
# ---------------------------------------------------------------------------
class GithubFetchError < StandardError; end

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

def github_get(path)
  token = ENV.fetch('GITHUB_TOKEN')
  req = Net::HTTP::Get.new(path)
  req['Authorization'] = "Bearer #{token}"
  req['Accept'] = 'application/vnd.github+json'
  req['X-GitHub-Api-Version'] = '2022-11-28'
  res = http_session.request(req)
  raise GithubFetchError, "GitHub API #{res.code} for #{path}: #{res.body[0, 200]}" unless res.code.to_i == 200

  res
rescue Net::OpenTimeout, Net::ReadTimeout, Errno::ETIMEDOUT, Errno::ECONNRESET, SocketError, OpenSSL::SSL::SSLError => e
  @http_session = nil # force a fresh connection on the next attempt
  raise GithubFetchError, "#{e.class}: #{e.message}"
end

def with_retries(label)
  attempt = 0
  begin
    attempt += 1
    yield
  rescue GithubFetchError => e
    if attempt < MAX_ATTEMPTS
      delay = RETRY_BACKOFF[attempt - 1] || RETRY_BACKOFF.last
      warn "    retry #{attempt}/#{MAX_ATTEMPTS - 1} for #{label} after #{e.message} (waiting #{delay}s)"
      sleep delay
      retry
    end
    warn "    giving up on #{label} after #{MAX_ATTEMPTS} attempts: #{e.message}"
    :fetch_failed
  end
end

def next_page_path(res)
  link = res['Link']
  link&.split(',')&.map(&:strip)&.find { |p| p.end_with?('rel="next"') }&.match(/<https:\/\/api\.github\.com([^>]+)>/)&.captures&.first
end

# ---------------------------------------------------------------------------
# Issues: real closing commit via the timeline API.
# ---------------------------------------------------------------------------
def fetch_closing_commit_live(issue_number)
  path = "/repos/#{OWNER}/#{REPO}/issues/#{issue_number}/timeline?per_page=100"
  closing_commit = nil
  loop do
    res = github_get(path)
    events = JSON.parse(res.body)
    events.each do |ev|
      closing_commit = ev['commit_id'] if ev['event'] == 'closed' && ev['commit_id']
    end
    next_path = next_page_path(res)
    break unless next_path

    path = next_path
  end
  closing_commit
end

def load_mock_closing_shas
  JSON.parse(File.read(ENV.fetch('CHANGELOG_FIX_MOCK_CLOSING_SHAS')))
end

# ---------------------------------------------------------------------------
# PRs: every merged PR via the pulls API, bulk-fetched (merge_commit_sha
# comes back in the list response itself - no per-PR call needed).
# ---------------------------------------------------------------------------
def fetch_all_merged_prs_live
  prs = []
  page = 1
  loop do
    path = "/repos/#{OWNER}/#{REPO}/pulls?state=closed&sort=created&direction=asc&per_page=100&page=#{page}"
    res = github_get(path)
    items = JSON.parse(res.body)
    break if items.empty?

    items.each do |pr|
      next unless pr['merged_at'] && pr['merge_commit_sha']

      login = pr.dig('user', 'login')
      prs << {
        'number' => pr['number'],
        'title' => pr['title'],
        'sha' => pr['merge_commit_sha'],
        'login' => login,
        'bot' => pr.dig('user', 'type') == 'Bot'
      }
    end
    warn "  fetched page #{page} (#{prs.size} merged PRs so far)"
    page += 1
  end
  prs
end

def load_mock_merged_prs
  JSON.parse(File.read(ENV.fetch('CHANGELOG_FIX_MOCK_MERGED_PRS')))
end

def pr_bullet_line(pr)
  title = escape_markdown(pr['title'])
  login = pr['login']
  author_url = pr['bot'] ? "https://github.com/apps/#{login.sub(/\[bot\]\z/, '')}" : "https://github.com/#{login}"
  "- #{title} [\\##{pr['number']}](https://github.com/#{OWNER}/#{REPO}/pull/#{pr['number']}) ([#{login}](#{author_url}))\n"
end

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
preamble, sections = parse_changelog(CHANGELOG_PATH)

mock_closing_shas = ENV['CHANGELOG_FIX_MOCK_CLOSING_SHAS'] ? load_mock_closing_shas : nil
mock_merged_prs = ENV['CHANGELOG_FIX_MOCK_MERGED_PRS'] ? load_mock_merged_prs : nil

# tag order, oldest -> newest, real-release sections only (Unreleased has no date)
ordered_tags = sections.reverse.reject { |s| s[:tag] == 'Unreleased' }.map { |s| s[:tag] }
tag_index = ordered_tags.each_with_index.to_h
ordered_tags_set = ordered_tags.to_set

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

unreleased_subs = sections_body['Unreleased'] || SUBSECTION_ORDER.each_with_object({}) { |h, acc| acc[h] = [] }
had_unreleased = sections.any? { |s| s[:tag] == 'Unreleased' }

# Strip all pre-existing issue and PR bullets - both get fully rebuilt below.
sections_body.each_value do |subs|
  SUBSECTION_ORDER.each { |h| subs[h] = [] }
end

# ---------------------------------------------------------------------------
# Issues
# ---------------------------------------------------------------------------
total_issues = issue_bullets.size
warn "\n=== Issues: #{total_issues} bullets to re-check ==="

issue_corrections = { moved: [], dropped_no_fix: [], dropped_not_on_branch: [], unchanged: [], skipped_error: [] }

issue_bullets.each_with_index do |b, i|
  warn "[#{i + 1}/#{total_issues}] ##{b[:number]} (was under #{b[:tag]})" if ((i + 1) % 5).zero? || (i + 1) == total_issues

  closing_sha =
    if mock_closing_shas
      mock_closing_shas[b[:number].to_s]
    else
      with_retries("issue ##{b[:number]}") { fetch_closing_commit_live(b[:number]) }
    end

  if closing_sha == :fetch_failed
    issue_corrections[:skipped_error] << b
    target_subs = b[:tag] == 'Unreleased' ? unreleased_subs : sections_body[b[:tag]]
    target_subs[b[:header]] << b[:line]
    next
  end

  if closing_sha.nil?
    issue_corrections[:dropped_no_fix] << b
    next
  end

  containing = tags_containing(closing_sha) & ordered_tags_set
  correct_tag =
    if containing.any?
      containing.min_by { |t| tag_index[t] }
    elsif is_ancestor?(closing_sha, BRANCH_REF)
      'Unreleased'
    end

  if correct_tag.nil?
    issue_corrections[:dropped_not_on_branch] << b.merge(closing_sha: closing_sha)
    next
  end

  target_subs = correct_tag == 'Unreleased' ? unreleased_subs : sections_body[correct_tag]
  target_subs[b[:header]] << b[:line]
  if correct_tag == b[:tag]
    issue_corrections[:unchanged] << b
  else
    issue_corrections[:moved] << b.merge(correct_tag: correct_tag, closing_sha: closing_sha)
  end
end

# ---------------------------------------------------------------------------
# PRs
# ---------------------------------------------------------------------------
warn "\n=== Merged pull requests: fetching authoritative list ==="
merged_prs = mock_merged_prs || with_retries('merged PR list') { fetch_all_merged_prs_live }
merged_prs = [] if merged_prs == :fetch_failed
total_prs = merged_prs.size
warn "#{total_prs} merged PRs to place"

pr_corrections = { placed: [], dropped_not_on_branch: [] }
pr_header = '**Merged pull requests:**'

merged_prs.each_with_index do |pr, i|
  warn "[#{i + 1}/#{total_prs}] ##{pr['number']}" if ((i + 1) % 25).zero? || (i + 1) == total_prs

  sha = pr['sha']
  containing = tags_containing(sha) & ordered_tags_set
  correct_tag =
    if containing.any?
      containing.min_by { |t| tag_index[t] }
    elsif is_ancestor?(sha, BRANCH_REF)
      'Unreleased'
    end

  if correct_tag.nil?
    pr_corrections[:dropped_not_on_branch] << pr
    next
  end

  target_subs = correct_tag == 'Unreleased' ? unreleased_subs : sections_body[correct_tag]
  target_subs[pr_header] << [pr['number'], pr_bullet_line(pr)]
  pr_corrections[:placed] << pr.merge('tag' => correct_tag)
end

# sort each subsection's bullets descending by number, per convention
sections_body.each_value do |subs|
  SUBSECTION_ORDER.each do |h|
    subs[h] = subs[h].map { |entry| entry.is_a?(Array) ? entry : [bullet_number_and_kind(entry)[0], entry] }
                      .sort_by { |num, _| -num.to_i }
                      .map { |_, line| line }
  end
end
unreleased_subs.each_key do |h|
  unreleased_subs[h] = unreleased_subs[h].map { |entry| entry.is_a?(Array) ? entry : [bullet_number_and_kind(entry)[0], entry] }
                                          .sort_by { |num, _| -num.to_i }
                                          .map { |_, line| line }
end

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

warn "\n=== Issue corrections ==="
warn "Moved to correct tag: #{issue_corrections[:moved].size}"
issue_corrections[:moved].each { |c| warn "  ##{c[:number]} #{c[:tag]} -> #{c[:correct_tag]} (via #{c[:closing_sha][0, 8]})" }
warn "Dropped (no closing commit - stale/wontfix/duplicate): #{issue_corrections[:dropped_no_fix].size}"
issue_corrections[:dropped_no_fix].each { |c| warn "  ##{c[:number]} (was under #{c[:tag]})" }
warn "Dropped (closing commit not on this branch at all): #{issue_corrections[:dropped_not_on_branch].size}"
issue_corrections[:dropped_not_on_branch].each { |c| warn "  ##{c[:number]} (was under #{c[:tag]}, closing commit #{c[:closing_sha][0, 8]})" }
warn "Left unchanged after lookup failure (network error - not dropped, not moved): #{issue_corrections[:skipped_error].size}"
issue_corrections[:skipped_error].each { |c| warn "  ##{c[:number]} (still under #{c[:tag]})" }
warn "Already correct: #{issue_corrections[:unchanged].size}"

warn "\n=== PR placement ==="
warn "Placed: #{pr_corrections[:placed].size}"
warn "Dropped (not on this branch at all): #{pr_corrections[:dropped_not_on_branch].size}"
pr_corrections[:dropped_not_on_branch].each { |c| warn "  ##{c['number']} #{c['title']}" }
