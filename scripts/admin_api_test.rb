#!/usr/bin/env ruby
# frozen_string_literal: true

# 管理端 API 冒烟测试：按顺序验证鉴权、概览、企业、消息、队列等接口。
# 用法:
#   ADMIN_PASSWORD=*** ruby scripts/admin_api_test.rb
#   BASE_URL=http://127.0.0.1:18081 ADMIN_USERNAME=admin ADMIN_PASSWORD=*** ruby scripts/admin_api_test.rb
#
# 报告输出到 tmp/admin-api-test-report.json 与 tmp/admin-api-test-report.md。

require 'json'
require 'net/http'
require 'time'
require 'uri'
require 'fileutils'

BASE_URL = ENV.fetch('BASE_URL', 'http://127.0.0.1:18081').freeze
USERNAME = ENV.fetch('ADMIN_USERNAME', 'admin')
PASSWORD = ENV.fetch('ADMIN_PASSWORD', nil)
REPORT_DIR = ENV.fetch('REPORT_DIR', File.expand_path('../tmp', __dir__))
TIMEOUT_SECONDS = 10

class ApiClient
  attr_reader :cookies

  def initialize(base_url)
    @base_url = base_url
    @cookies = {}
  end

  def get(path, token: nil)
    request(:get, path, token: token)
  end

  def post(path, body: nil, token: nil)
    request(:post, path, body: body, token: token)
  end

  def patch(path, body: nil, token: nil)
    request(:patch, path, body: body, token: token)
  end

  def put(path, body: nil, token: nil)
    request(:put, path, body: body, token: token)
  end

  private

  def request(method, path, body: nil, token: nil)
    uri = URI.join(@base_url.end_with?('/') ? @base_url : "#{@base_url}/", path.delete_prefix('/'))
    http = Net::HTTP.new(uri.host, uri.port)
    http.use_ssl = uri.scheme == 'https'
    http.open_timeout = TIMEOUT_SECONDS
    http.read_timeout = TIMEOUT_SECONDS

    req = build_request(method, uri)
    req['Accept'] = 'application/json'
    req['Authorization'] = "Bearer #{token}" if token
    req['Cookie'] = @cookies.map { |k, v| "#{k}=#{v}" }.join('; ') unless @cookies.empty?
    if body
      req['Content-Type'] = 'application/json'
      req.body = JSON.generate(body)
    end

    response = http.request(req)
    store_cookies(response)
    Response.new(status: response.code.to_i, body: parse_json(response.body),
                 headers: response.each_header.to_h)
  rescue StandardError => e
    Response.new(status: 0, body: { 'message' => e.message }, headers: {})
  end

  def build_request(method, uri)
    case method
    when :get then Net::HTTP::Get.new(uri)
    when :post then Net::HTTP::Post.new(uri)
    when :patch then Net::HTTP::Patch.new(uri)
    when :put then Net::HTTP::Put.new(uri)
    else raise ArgumentError, "unsupported method: #{method}"
    end
  end

  def store_cookies(response)
    Array(response.get_fields('set-cookie')).each do |raw|
      pair = raw.split(';', 2).first
      name, value = pair.split('=', 2)
      next if name.nil? || value.nil?

      @cookies[name.strip] = value.strip
    end
  end

  def parse_json(raw)
    return {} if raw.nil? || raw.empty?

    JSON.parse(raw)
  rescue JSON::ParserError
    { 'raw' => raw }
  end
end

Response = Struct.new(:status, :body, :headers, keyword_init: true)

class Runner
  def initialize(client)
    @client = client
    @results = []
    @token = nil
  end

  def run
    health
    login_rejects_wrong_password
    login_succeeds
    overview_requires_token
    overview_matches_version_header
    list_corps
    toggle_corp_status
    corp_detail
    corp_detail_not_found
    corp_create_rejects_invalid_id
    corp_batch_requires_ids
    list_messages
    admin_health
    export_messages_csv
    export_audit_csv
    message_not_found
    queue_overview
    rocketmq_overview
    rocketmq_groups
    rocketmq_topics
    rocketmq_config
    rocketmq_retry
    rocketmq_recent_messages
    list_settings
    update_setting_rejects_invalid_value
    setting_reset_rejects_unknown_key
    system_info
    alert_status
    retention_preview
    retention_requires_confirmation
    audit_logs
    corp_secret_is_masked
    corp_reveal_rejects_wrong_password
    corp_rotate_rejects_short_secret
    refresh_session
    logout
    self
  end

  attr_reader :results, :token

  private

  def check(name, category: 'api')
    response = yield
    ok, detail = evaluate(name, response)
    record(name, category, response, ok, detail)
    response
  rescue StandardError => e
    record(name, category, nil, false, e.message)
    nil
  end

  def evaluate(name, response)
    case name
    when 'health' then [response.status == 200 && response.body['status'] == 'UP', "status=#{response.status}"]
    when 'login_rejects_wrong_password'
      [response.status == 401 && response.body['code'] == 'INVALID_CREDENTIALS', "code=#{response.body['code']}"]
    when 'login_succeeds'
      ok = response.status == 200 && !!response.body['accessToken']
      [ok, ok ? "user=#{response.body.dig('user', 'username')}" : "status=#{response.status}"]
    when 'overview_requires_token' then [response.status == 401, "status=#{response.status}"]
    when 'overview_matches_version_header'
      version = response.headers['x-app-version']
      ok = response.status == 200 && !version.to_s.empty? && response.body['version'] == version
      [ok, "body=#{response.body['version']} header=#{version}"]
    when 'list_corps' then [response.status == 200 && response.body.is_a?(Array), "count=#{response.body.is_a?(Array) ? response.body.length : 'n/a'}"]
    when 'toggle_corp_status' then [response.status == 200, "status=#{response.status}"]
    when 'corp_detail'
      body = response.body
      ok = response.status == 200 && body.is_a?(Hash) && body.key?('redisReachable') &&
           body.key?('messageTotal') && body.key?('secretMasked') && !body.key?('secret')
      [ok, "id=#{body.is_a?(Hash) ? body['id'] : 'n/a'}"]
    when 'corp_detail_not_found'
      [response.status == 404 && response.body['code'] == 'CORP_NOT_FOUND', "code=#{response.body['code']}"]
    when 'corp_create_rejects_invalid_id'
      [response.status == 400 && response.body['code'] == 'CORP_ID_INVALID', "code=#{response.body['code']}"]
    when 'corp_batch_requires_ids'
      [response.status == 400 && response.body['code'] == 'CORP_IDS_EMPTY', "code=#{response.body['code']}"]
    when 'list_messages' then [response.status == 200 && response.body.key?('items'), "items=#{response.body['items']&.length}"]
    when 'admin_health'
      ok = response.status == 200 && %w[UP DEGRADED].include?(response.body['status']) &&
           response.body['database'].is_a?(Hash) && response.body['corps'].is_a?(Array)
      [ok, "status=#{response.body['status']} corps=#{response.body['corps'].is_a?(Array) ? response.body['corps'].length : 'n/a'}"]
    when 'export_messages_csv'
      type = response.headers['content-type'].to_s
      ok = response.status == 200 && type.include?('text/csv') && response.headers.key?('content-disposition')
      [ok, "type=#{type}"]
    when 'export_audit_csv'
      type = response.headers['content-type'].to_s
      ok = response.status == 200 && type.include?('text/csv') && response.headers.key?('content-disposition')
      [ok, "type=#{type}"]
    when 'message_not_found'
      [response.status == 404 && response.body['code'] == 'MESSAGE_NOT_FOUND', "code=#{response.body['code']}"]
    when 'queue_overview'
      [response.status == 200 && response.body.key?('redisMqEnabled'), "status=#{response.status}"]
    when 'rocketmq_overview'
      ok = response.status == 200 && response.body.key?('available') &&
           response.body.key?('namesrvAddr')
      [ok, "available=#{response.body['available']} namesrv=#{response.body['namesrvAddr']}"]
    when 'rocketmq_groups'
      [response.status == 200 && response.body.is_a?(Array), "count=#{response.body.is_a?(Array) ? response.body.length : 'n/a'}"]
    when 'rocketmq_topics'
      [response.status == 200 && response.body.is_a?(Array), "count=#{response.body.is_a?(Array) ? response.body.length : 'n/a'}"]
    when 'rocketmq_config'
      ok = response.status == 200 && response.body.key?('items') &&
           response.body['items'].is_a?(Array)
      [ok, "items=#{response.body['items'].is_a?(Array) ? response.body['items'].length : 'n/a'}"]
    when 'rocketmq_retry'
      [response.status == 200 && response.body.key?('available'), "available=#{response.body['available']}"]
    when 'rocketmq_recent_messages'
      ok = response.status == 200 && response.body['messages'].is_a?(Array) &&
           response.body.key?('scannedTopics')
      [ok, "rows=#{response.body['messages'].is_a?(Array) ? response.body['messages'].length : 'n/a'}"]
    when 'list_settings'
      ok = response.status == 200 && response.body.is_a?(Array) && !response.body.empty? &&
           response.body.all? { |item| item.key?('applyMode') && item.key?('unit') }
      [ok, "count=#{response.body.is_a?(Array) ? response.body.length : 'n/a'}"]
    when 'update_setting_rejects_invalid_value'
      [response.status == 400 && response.body['code'] == 'SETTING_INVALID', "code=#{response.body['code']}"]
    when 'setting_reset_rejects_unknown_key'
      [response.status == 400 && response.body['code'] == 'SETTING_NOT_FOUND', "code=#{response.body['code']}"]
    when 'system_info'
      body = response.body
      ok = response.status == 200 && !body['version'].to_s.empty? &&
           body['runtimeSettings'].is_a?(Array) && body.key?('databaseReachable') &&
           !body['jdbcUrl'].to_s.include?('password=')
      [ok, "version=#{body['version']} runtime=#{body['runtimeSettings'].is_a?(Array) ? body['runtimeSettings'].length : 'n/a'}"]
    when 'alert_status'
      ok = response.status == 200 && response.body.key?('enabled') && response.body.key?('webhookConfigured')
      [ok, "enabled=#{response.body['enabled']} webhook=#{response.body['webhookConfigured']}"]
    when 'retention_preview'
      ok = response.status == 200 && response.body['categories'].is_a?(Array)
      [ok, "categories=#{response.body['categories']&.length}"]
    when 'retention_requires_confirmation'
      ok = response.status == 400 && %w[RETENTION_DISABLED RETENTION_CONFIRM_REQUIRED]
           .include?(response.body['code'])
      [ok, "code=#{response.body['code']}"]
    when 'audit_logs'
      ok = response.status == 200 && response.body['items'].is_a?(Array)
      [ok, "items=#{response.body['items']&.length}"]
    when 'corp_secret_is_masked'
      corps = response.body
      ok = response.status == 200 && corps.is_a?(Array) &&
           corps.all? { |corp| corp.key?('secretMasked') && !corp.key?('secret') }
      [ok, "count=#{corps.is_a?(Array) ? corps.length : 'n/a'}"]
    when 'corp_reveal_rejects_wrong_password'
      [response.status == 401 && response.body['code'] == 'INVALID_CREDENTIALS', "code=#{response.body['code']}"]
    when 'corp_rotate_rejects_short_secret'
      [response.status == 400 && response.body['code'] == 'CORP_SECRET_INVALID', "code=#{response.body['code']}"]
    when 'refresh_session' then [response.status == 200 && !!response.body['accessToken'], "status=#{response.status}"]
    when 'logout' then [[200, 204].include?(response.status), "status=#{response.status}"]
    else [response.status.to_i.between?(200, 299), "status=#{response.status}"]
    end
  end

  def record(name, category, response, ok, detail)
    @results << {
      'name' => name,
      'category' => category,
      'passed' => ok,
      'status' => response&.status,
      'detail' => detail,
      'body' => compact_body(response&.body),
      'trace_id' => response&.headers&.fetch('x-trace-id', nil)
    }
  end

  def compact_body(body)
    return nil if body.nil?

    text = JSON.generate(body)
    text.length > 500 ? "#{text[0, 500]}..." : text
  end

  def health
    check('health') { @client.get('/actuator/health') }
  end

  def login_rejects_wrong_password
    check('login_rejects_wrong_password') do
      @client.post('/api/admin/auth/login', body: { username: USERNAME, password: "#{PASSWORD}-wrong" })
    end
  end

  def login_succeeds
    response = check('login_succeeds') do
      @client.post('/api/admin/auth/login', body: { username: USERNAME, password: PASSWORD })
    end
    @token = response&.body&.fetch('accessToken', nil)
  end

  def overview_requires_token
    check('overview_requires_token') { @client.get('/api/admin/overview') }
  end

  def overview_matches_version_header
    check('overview_matches_version_header') { @client.get('/api/admin/overview', token: @token) }
  end

  def list_corps
    response = check('list_corps') { @client.get('/api/admin/corps', token: @token) }
    @corp_id = response.body.first['id'] if response&.status == 200 && response.body.is_a?(Array) && !response.body.empty?
  end

  def toggle_corp_status
    unless @corp_id
      record('toggle_corp_status', 'api', nil, true, 'skipped: 无企业数据')
      return
    end
    current = @client.get('/api/admin/corps', token: @token).body
                   .find { |corp| corp['id'] == @corp_id }
                   .fetch('status', 0)
    check('toggle_corp_status') do
      @client.patch("/api/admin/corps/#{@corp_id}/status",
                    body: { enabled: current <= 0 }, token: @token)
    end
    # 恢复原始状态，避免测试改变本地数据
    @client.patch("/api/admin/corps/#{@corp_id}/status", body: { enabled: current.positive? }, token: @token)
  end

  def list_messages
    check('list_messages') { @client.get('/api/admin/messages?pageSize=2', token: @token) }
  end

  def corp_detail
    unless @corp_id
      record('corp_detail', 'api', nil, true, 'skipped: 无企业数据')
      return
    end
    check('corp_detail') { @client.get("/api/admin/corps/#{@corp_id}", token: @token) }
  end

  def corp_detail_not_found
    check('corp_detail_not_found') do
      @client.get('/api/admin/corps/999999999', token: @token)
    end
  end

  def corp_create_rejects_invalid_id
    check('corp_create_rejects_invalid_id') do
      @client.post('/api/admin/corps',
                   body: { corpId: 'bad id!', corpName: 'smoke', secret: '12345678', prikey: 'x' },
                   token: @token)
    end
  end

  def corp_batch_requires_ids
    check('corp_batch_requires_ids') do
      @client.patch('/api/admin/corps/batch-status', body: { ids: [], enabled: true }, token: @token)
    end
  end

  def admin_health
    check('admin_health') { @client.get('/api/admin/health', token: @token) }
  end

  def export_messages_csv
    check('export_messages_csv') { @client.get('/api/admin/messages/export', token: @token) }
  end

  def export_audit_csv
    check('export_audit_csv') { @client.get('/api/admin/audit-logs/export', token: @token) }
  end

  def message_not_found
    check('message_not_found') { @client.get('/api/admin/messages/999999999', token: @token) }
  end

  def queue_overview
    check('queue_overview') { @client.get('/api/admin/queue/overview', token: @token) }
  end

  def rocketmq_overview
    check('rocketmq_overview') { @client.get('/api/admin/rocketmq/overview', token: @token) }
  end

  def rocketmq_groups
    check('rocketmq_groups') { @client.get('/api/admin/rocketmq/groups', token: @token) }
  end

  def rocketmq_topics
    check('rocketmq_topics') { @client.get('/api/admin/rocketmq/topics', token: @token) }
  end

  def rocketmq_config
    check('rocketmq_config') { @client.get('/api/admin/rocketmq/config', token: @token) }
  end

  def rocketmq_retry
    check('rocketmq_retry') { @client.get('/api/admin/rocketmq/retry?limit=5', token: @token) }
  end

  def rocketmq_recent_messages
    check('rocketmq_recent_messages') do
      @client.get('/api/admin/rocketmq/messages/recent?limit=20', token: @token)
    end
  end

  def list_settings
    check('list_settings') { @client.get('/api/admin/settings', token: @token) }
  end

  def update_setting_rejects_invalid_value
    check('update_setting_rejects_invalid_value') do
      @client.put('/api/admin/settings',
                  body: { key: 'alert.cooldown-minutes', value: 'abc' }, token: @token)
    end
  end

  def alert_status
    check('alert_status') { @client.get('/api/admin/alerts/status', token: @token) }
  end

  def setting_reset_rejects_unknown_key
    check('setting_reset_rejects_unknown_key') do
      @client.post('/api/admin/settings/reset',
                   body: { key: 'runtime.not-exists' }, token: @token)
    end
  end

  def system_info
    check('system_info') { @client.get('/api/admin/system', token: @token) }
  end

  def retention_preview
    check('retention_preview') { @client.get('/api/admin/retention/preview', token: @token) }
  end

  def retention_requires_confirmation
    check('retention_requires_confirmation') do
      @client.post('/api/admin/retention/execute',
                   body: { category: 'ORIGINAL_MSG', confirm: 'NO' }, token: @token)
    end
  end

  def audit_logs
    check('audit_logs') { @client.get('/api/admin/audit-logs?limit=5', token: @token) }
  end

  def corp_secret_is_masked
    check('corp_secret_is_masked') { @client.get('/api/admin/corps', token: @token) }
  end

  def corp_reveal_rejects_wrong_password
    unless @corp_id
      record('corp_reveal_rejects_wrong_password', 'api', nil, true, 'skipped: 无企业数据')
      return
    end
    check('corp_reveal_rejects_wrong_password') do
      @client.post("/api/admin/corps/#{@corp_id}/reveal",
                   body: { password: "#{PASSWORD}-wrong" }, token: @token)
    end
  end

  def corp_rotate_rejects_short_secret
    unless @corp_id
      record('corp_rotate_rejects_short_secret', 'api', nil, true, 'skipped: 无企业数据')
      return
    end
    check('corp_rotate_rejects_short_secret') do
      @client.put("/api/admin/corps/#{@corp_id}/secret",
                  body: { secret: 'short' }, token: @token)
    end
  end

  def refresh_session
    check('refresh_session') { @client.post('/api/admin/auth/refresh') }
  end

  def logout
    check('logout') { @client.post('/api/admin/auth/logout', token: @token) }
  end
end

def write_reports(results, base_url)
  FileUtils.mkdir_p(REPORT_DIR)
  passed = results.count { |r| r['passed'] }
  summary = {
    'generated_at' => Time.now.iso8601,
    'base_url' => base_url,
    'total' => results.length,
    'passed' => passed,
    'failed' => results.length - passed,
    'results' => results
  }

  json_path = File.join(REPORT_DIR, 'admin-api-test-report.json')
  md_path = File.join(REPORT_DIR, 'admin-api-test-report.md')
  File.write(json_path, JSON.pretty_generate(summary))

  lines = []
  lines << '# 管理端 API 测试报告'
  lines << ''
  lines << "- 执行时间：#{summary['generated_at']}"
  lines << "- 目标地址：#{base_url}"
  lines << "- 用例：#{summary['total']}，通过：#{passed}，失败：#{summary['failed']}"
  lines << ''
  lines << '| 用例 | 结果 | HTTP | 说明 |'
  lines << '| --- | --- | --- | --- |'
  results.each do |result|
    mark = result['passed'] ? 'PASS' : 'FAIL'
    detail = result['detail'].to_s.gsub('|', '\\|')
    lines << "| #{result['name']} | #{mark} | #{result['status'] || '-'} | #{detail} |"
  end
  lines << ''
  File.write(md_path, lines.join("\n"))

  [json_path, md_path, summary]
end

if PASSWORD.nil? || PASSWORD.empty?
  warn '缺少 ADMIN_PASSWORD 环境变量'
  exit 2
end

client = ApiClient.new(BASE_URL)
runner = Runner.new(client).run
json_path, md_path, summary = write_reports(runner.results, BASE_URL)

puts "用例 #{summary['total']}，通过 #{summary['passed']}，失败 #{summary['failed']}"
puts "JSON 报告：#{json_path}"
puts "Markdown 报告：#{md_path}"
exit(summary['failed'].zero? ? 0 : 1)
