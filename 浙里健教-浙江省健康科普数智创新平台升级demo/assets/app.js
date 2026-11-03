(function () {
  'use strict';

  var $ = function (selector, root) {
    return (root || document).querySelector(selector);
  };
  var $$ = function (selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  };
  var routeOrder = ['overview', 'corpus', 'creation', 'media', 'experts', 'tiantian', 'analytics'];
  var routeNames = {
    overview: '综合工作台',
    corpus: '健康教育科普语料库',
    creation: '健康科普内容智能创作',
    media: '健康科普多媒体资源',
    experts: '健康科普专家库',
    tiantian: '“天天”智能问答',
    analytics: '健康素养监测分析'
  };
  var pageNames = {
    overview: '系统总览',
    corpus: '科普语料库',
    creation: '内容智能创作',
    media: '多媒体资源管理',
    experts: '专家库管理',
    tiantian: '“天天”智能问答',
    analytics: '监测数据分析'
  };
  var tenantOptions = [
    { id: 'province', name: '浙江省疾病预防控制中心', scope: '全省业务管理', icon: 'landmark' },
    { id: 'hangzhou', name: '杭州市健康教育中心', scope: '杭州市业务管理', icon: 'building-2' },
    { id: 'ningbo', name: '宁波市健康教育中心', scope: '宁波市业务管理', icon: 'building-2' }
  ];
  var initialTasks = [
    { id: 'T-260716-018', title: '复核高血压专题新增语料', module: '科普语料库', route: 'corpus', priority: '高', due: '今天 16:30', done: false },
    { id: 'T-260716-017', title: '审核“血压正常后能停药吗”短视频脚本', module: '内容智能创作', route: 'creation', priority: '高', due: '今天 17:00', done: false },
    { id: 'T-260716-015', title: '确认基层高血压讲座预约', module: '专家库', route: 'experts', priority: '中', due: '明天 10:00', done: false },
    { id: 'T-260716-012', title: '处理“天天”问答负反馈工单', module: '智能问答', route: 'tiantian', priority: '中', due: '明天 12:00', done: false },
    { id: 'T-260716-009', title: '复核健康素养监测异常记录', module: '监测分析', route: 'analytics', priority: '低', due: '7 月 18 日', done: false }
  ];
  var initialNotifications = [
    { id: 'N-01', title: '采集任务已完成', text: '“夏季慢病健康教育”采集任务新增有效语料 69 条。', time: '5 分钟前', read: false, route: 'corpus' },
    { id: 'N-02', title: '专家审核已退回', text: '脚本需要补充不可自行停药的风险提示。', time: '18 分钟前', read: false, route: 'creation' },
    { id: 'N-03', title: '预约等待确认', text: '7 月 23 日高血压社区讲座等待专家确认。', time: '43 分钟前', read: false, route: 'experts' },
    { id: 'N-04', title: '数据批次校验完成', text: '2026 年度健康素养监测批次已生成质量报告。', time: '1 小时前', read: false, route: 'analytics' },
    { id: 'N-05', title: '资源发布完成', text: '高血压用药安全短视频已同步至 3 个服务渠道。', time: '2 小时前', read: false, route: 'media' }
  ];

  function seedState() {
    return {
      route: 'overview',
      tenantId: 'province',
      tasks: initialTasks,
      notifications: initialNotifications,
      taskAlertCount: 0,
      notificationAlertCount: 0,
      corpusFilter: 'all',
      corpusRunning: false,
      creation: { status: 'draft', version: 3, generated: true, revised: false, originality: false },
      selectedExpertId: 'LIB-12-P001',
      chatBusy: false,
      population: 'all',
      analyticsTab: 'overview',
      chartPoints: []
    };
  }

  var state = seedState();
  var storageKey = 'zjhe_saas_ui_state_v1';
  var drawerType = null;
  var commandItems = [];
  var commandActiveIndex = 0;
  var resizeTimer = null;
  var expertDirectory = window.ZJ_EXPERT_LIBRARY || { source: {}, libraries: [] };
  var expertRecords = [];
  var expertFilteredRecords = [];
  var expertPage = 1;
  var expertPageSize = 9;
  var zhejiangMapChart = null;
  var zhejiangMapRegistered = false;

  function escapeHTML(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function icon(name, className) {
    return '<svg class="ui-icon' + (className ? ' ' + escapeHTML(className) : '') + '" aria-hidden="true" focusable="false"><use href="#i-' + escapeHTML(name) + '"></use></svg>';
  }

  function delay(ms) {
    return new Promise(function (resolve) { window.setTimeout(resolve, ms); });
  }

  function loadState() {
    try {
      var saved = JSON.parse(window.localStorage.getItem(storageKey) || 'null');
      if (!saved) return;
      if (saved.tenantId) state.tenantId = saved.tenantId;
      if (Array.isArray(saved.tasks)) state.tasks = saved.tasks;
      if (Array.isArray(saved.notifications)) state.notifications = saved.notifications;
      if (saved.creation) state.creation = Object.assign({}, state.creation, saved.creation);
    } catch (error) {
      state = seedState();
    }
  }

  function persistState() {
    try {
      window.localStorage.setItem(storageKey, JSON.stringify({
        tenantId: state.tenantId,
        tasks: state.tasks,
        notifications: state.notifications,
        creation: state.creation
      }));
    } catch (error) {
      return;
    }
  }

  function formatDate(date) {
    var pad = function (value) { return String(value).padStart(2, '0'); };
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  function formatTime(date) {
    var pad = function (value) { return String(value).padStart(2, '0'); };
    return pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  function updateTimeLabels() {
    var now = new Date();
    var full = formatDate(now) + ' ' + formatTime(now);
    var overviewTime = $('#overviewDataTime');
    if (overviewTime) overviewTime.textContent = '数据更新时间：' + full;
    var analyticsDate = $('#analyticsDate');
    if (analyticsDate) analyticsDate.textContent = formatDate(now);
    var analyticsDataTime = $('#analyticsDataTime');
    if (analyticsDataTime) analyticsDataTime.textContent = '数据更新于 ' + formatTime(now);
    var analyticsClock = $('#analyticsClock');
    if (analyticsClock) {
      var weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
      analyticsClock.textContent = formatTime(now) + ':' + String(now.getSeconds()).padStart(2, '0') + '　' + weekdays[now.getDay()];
    }
  }

  function toast(message, type, title) {
    var stack = $('#toastStack');
    if (!stack) return;
    var item = document.createElement('div');
    item.className = 'toast ' + (type || 'info');
    var toastIcon = type === 'success' ? 'circle-check' : type === 'warning' || type === 'error' ? 'triangle-alert' : 'info';
    item.innerHTML = '<span class="toast-mark">' + icon(toastIcon) + '</span>' +
      '<div><b>' + escapeHTML(title || (type === 'success' ? '操作成功' : '系统提示')) + '</b><small>' + escapeHTML(message) + '</small></div>';
    stack.appendChild(item);
    window.requestAnimationFrame(function () { item.classList.add('show'); });
    window.setTimeout(function () {
      item.classList.remove('show');
      window.setTimeout(function () { item.remove(); }, 240);
    }, 3000);
  }

  function openModal(options) {
    var backdrop = $('#modalBackdrop');
    var content = $('#modalContent');
    var win = $('#modalWindow');
    if (!backdrop || !content || !win) return;
    win.className = 'modal-window ' + (options.size ? 'modal-' + options.size : '');
    var actions = (options.actions || []).map(function (action) {
      return '<button class="' + (action.primary ? 'primary-btn' : 'ghost-btn') + '" data-modal-action="' + escapeHTML(action.action) + '">' + escapeHTML(action.label) + '</button>';
    }).join('');
    content.innerHTML = '<div class="modal-heading"><span class="section-kicker">' + escapeHTML(options.kicker || 'BUSINESS WORKFLOW') +
      '</span><h2 id="modalTitle">' + escapeHTML(options.title || '业务处理') + '</h2>' +
      (options.subtitle ? '<p>' + escapeHTML(options.subtitle) + '</p>' : '') + '</div>' +
      '<div class="modal-body">' + (options.body || '') + '</div>' + (actions ? '<div class="modal-actions">' + actions + '</div>' : '');
    backdrop.classList.remove('hidden');
    backdrop.classList.add('open');
    document.body.classList.add('modal-open');
    window.setTimeout(function () {
      var first = $('input, textarea, select, [data-modal-action]', content);
      if (first) first.focus();
    }, 20);
  }

  function closeModal() {
    var backdrop = $('#modalBackdrop');
    if (!backdrop) return;
    backdrop.classList.add('hidden');
    backdrop.classList.remove('open');
    document.body.classList.remove('modal-open');
  }

  function currentTenant() {
    return tenantOptions.find(function (item) { return item.id === state.tenantId; }) || tenantOptions[0];
  }

  function updateTenantUI() {
    var tenant = currentTenant();
    var switcher = $('#tenantSwitcher');
    if (!switcher) return;
    var mark = $('.tenant-emblem', switcher);
    var name = $('b', switcher);
    if (mark) mark.textContent = tenant.mark;
    if (name) name.textContent = tenant.name;
  }

  function updateCounters() {
    var taskAlerts = Math.max(0, Number(state.taskAlertCount) || 0);
    var notificationAlerts = Math.max(0, Number(state.notificationAlertCount) || 0);
    var taskCounter = $('#workQueueBtn em');
    var noticeCounter = $('#notificationBtn em');
    var taskButton = $('#workQueueBtn');
    var noticeButton = $('#notificationBtn');
    if (taskCounter) {
      taskCounter.textContent = taskAlerts ? String(taskAlerts) : '';
      taskCounter.classList.toggle('hidden', !taskAlerts);
    }
    if (noticeCounter) {
      noticeCounter.textContent = notificationAlerts ? String(notificationAlerts) : '';
      noticeCounter.classList.toggle('hidden', !notificationAlerts);
    }
    if (taskButton) taskButton.classList.toggle('has-counter', Boolean(taskAlerts));
    if (noticeButton) noticeButton.classList.toggle('has-counter', Boolean(notificationAlerts));
  }

  function parseRoute() {
    var route = String(window.location.hash || '').replace(/^#\/?/, '').split('?')[0];
    return routeOrder.indexOf(route) === -1 ? 'overview' : route;
  }

  function renderRoute(route) {
    if (routeOrder.indexOf(route) === -1) route = 'overview';
    state.route = route;
    $$('.page-view').forEach(function (view) { view.classList.toggle('active', view.getAttribute('data-view') === route); });
    $$('.nav-item[data-route]').forEach(function (item) {
      var active = item.getAttribute('data-route') === route;
      item.classList.toggle('active', active);
      item.setAttribute('aria-current', active ? 'page' : 'false');
    });
    var pageName = $('#currentPageName');
    if (pageName) pageName.textContent = pageNames[route];
    var shell = $('#appShell');
    if (shell) shell.classList.remove('nav-open');
    var stage = $('.main-stage');
    if (stage) stage.scrollTop = 0;
    if (route === 'analytics') window.requestAnimationFrame(function () { drawTrendChart(); drawZhejiangMap(); });
  }

  function navigate(route) {
    if (routeOrder.indexOf(route) === -1) return;
    var nextHash = '#/' + route;
    if (window.location.hash === nextHash) renderRoute(route);
    else window.location.hash = nextHash;
  }

  function closeDrawer() {
    var drawer = $('#saasDrawer');
    var scrim = $('#drawerScrim');
    if (drawer) drawer.classList.add('hidden');
    if (scrim) scrim.classList.add('hidden');
    drawerType = null;
    $$('.module-tabs').forEach(function (tabs) {
      $$('button', tabs).forEach(function (button, index) { button.classList.toggle('active', index === 0); });
    });
  }

  function openDrawer(type, title, kicker, html) {
    drawerType = type;
    var drawer = $('#saasDrawer');
    var scrim = $('#drawerScrim');
    if ($('#drawerTitle')) $('#drawerTitle').textContent = title;
    if ($('#drawerKicker')) $('#drawerKicker').textContent = kicker || 'WORK CENTER';
    if ($('#drawerContent')) $('#drawerContent').innerHTML = html;
    if (drawer) drawer.classList.remove('hidden');
    if (scrim) scrim.classList.remove('hidden');
  }

  function taskDrawerHTML() {
    var remaining = state.tasks.filter(function (task) { return !task.done; });
    return '<div class="drawer-summary"><div><small>待处理</small><b>' + remaining.length + '</b></div><div><small>高优先级</small><b>' + remaining.filter(function (task) { return task.priority === '高'; }).length + '</b></div></div>' +
      '<div class="task-list">' + remaining.map(function (task) {
        var priorityClass = task.priority === '高' ? 'high' : task.priority === '中' ? 'medium' : 'low';
        var priorityIcon = task.priority === '高' ? 'triangle-alert' : task.priority === '中' ? 'info' : 'circle-check';
        return '<article class="task-item"><span class="task-priority ' + priorityClass + '" title="' + task.priority + '优先级">' + icon(priorityIcon) + '</span>' +
          '<div><b>' + escapeHTML(task.title) + '</b><small class="task-meta">' + escapeHTML(task.module) + ' · ' + escapeHTML(task.due) + '</small></div>' +
          '<div class="task-action"><button data-task-route="' + task.route + '">处理</button><button data-task-done="' + task.id + '">完成</button></div></article>';
      }).join('') + '</div>' + (remaining.length ? '' : '<div class="drawer-empty"><b>暂无待办</b><small>所有工作均已处理完成。</small></div>');
  }

  function notificationDrawerHTML() {
    return '<div class="drawer-summary"><div><small>未读消息</small><b>' + state.notifications.filter(function (item) { return !item.read; }).length + '</b></div><button data-notice-read-all="true">全部标为已读</button></div>' +
      '<div class="notification-list">' + state.notifications.map(function (item) {
        return '<article class="notification-item ' + (item.read ? 'read' : '') + '" data-notice-id="' + item.id + '"><i class="notification-dot"></i><div><b>' + escapeHTML(item.title) + '</b><p>' + escapeHTML(item.text) + '</p><small>' + escapeHTML(item.time) + '</small></div><button data-notice-route="' + item.route + '">查看</button></article>';
      }).join('') + '</div>';
  }

  function tenantDrawerHTML() {
    return '<div class="tenant-list">' + tenantOptions.map(function (tenant) {
      return '<button class="tenant-option ' + (tenant.id === state.tenantId ? 'active' : '') + '" data-tenant-id="' + tenant.id + '"><span>' + icon(tenant.icon) + '</span><div><b>' + tenant.name + '</b><small>' + tenant.scope + '</small></div><i>' + icon(tenant.id === state.tenantId ? 'check' : 'chevron-right') + '</i></button>';
    }).join('') + '</div><div class="modal-note">切换组织后，指标、任务和业务权限将按当前组织范围展示。</div>';
  }

  function auditDrawerHTML() {
    return '<div class="drawer-stat-grid"><div><small>今日操作</small><b>286</b></div><div><small>异常操作</small><b>0</b></div></div>' +
      '<div class="audit-table"><div class="audit-row head"><span>时间</span><span>操作人</span><span>业务动作</span><span>结果</span></div>' +
      '<div class="audit-row"><span>15:38</span><span>管理员</span><span>保存内容草稿 V1.3</span><span class="status approved">成功</span></div>' +
      '<div class="audit-row"><span>15:21</span><span>管理员</span><span>复核语料 ZJHE-C-00862</span><span class="status approved">成功</span></div>' +
      '<div class="audit-row"><span>14:52</span><span>系统任务</span><span>同步多媒体发布状态</span><span class="status approved">成功</span></div>' +
      '<div class="audit-row"><span>14:36</span><span>管理员</span><span>提交专家讲座预约</span><span class="status processing">处理中</span></div></div>';
  }

  function settingsDrawerHTML() {
    return '<div class="settings-list"><article><div><b>组织与成员</b><small>管理机构层级、成员与角色权限</small></div><button>进入配置</button></article>' +
      '<article><div><b>业务字典</b><small>疾病领域、目标人群、标签和来源字典</small></div><button>进入配置</button></article>' +
      '<article><div><b>渠道与集成</b><small>发布渠道、消息服务和浙里健教能力配置</small></div><button>进入配置</button></article>' +
      '<article><div><b>安全与审计</b><small>登录策略、数据权限和审计保留周期</small></div><button>进入配置</button></article></div>';
  }

  function moduleDrawerHTML(module, label) {
    var sets = {
      corpus: ['采集任务 ZJHE-CJ-20260716-03', '待复核语料 36 条', '已配置来源 12 个'],
      media: ['上传队列 4 项', '待发布任务 7 项', '发布渠道 5 个'],
      experts: ['讲座预约待确认 6 场', '本月执行活动 42 场', '满意度问卷待回收 3 份'],
      tiantian: ['高风险回答待复核 2 条', '负反馈工单待处理 3 条', '知识内容待更新 8 条']
    };
    var rows = sets[module] || [];
    return '<div class="drawer-summary"><div><small>当前栏目</small><b>' + escapeHTML(label) + '</b></div></div><div class="task-list">' + rows.map(function (row, index) {
      return '<article class="task-item"><span class="task-priority ' + (index === 0 ? 'high' : 'low') + '">' + String(index + 1).padStart(2, '0') + '</span><div><b>' + escapeHTML(row) + '</b><small class="task-meta">' + escapeHTML(routeNames[module] || '业务中心') + '</small></div><div class="task-action"><button data-module-route="' + module + '">查看</button></div></article>';
    }).join('') + '</div>';
  }

  function buildCommandItems() {
    commandItems = [
      { id: 'route-overview', name: '综合工作台', description: '查看业务态势与个人待办', route: 'overview', keywords: '首页 工作台 态势' },
      { id: 'route-corpus', name: '健康教育科普语料库', description: '语料检索、采集与人工复核', route: 'corpus', keywords: '语料 采集 复核 来源' },
      { id: 'route-creation', name: '健康科普内容智能创作', description: '脚本生成、审核、原创核验与合成', route: 'creation', keywords: '浙里健教 创作 脚本 审核 视频' },
      { id: 'route-media', name: '健康科普多媒体资源', description: '资源上传、标引和发布', route: 'media', keywords: '视频 图文 音频 发布' },
      { id: 'route-experts', name: '健康科普专家库', description: '专家档案、预约与活动管理', route: 'experts', keywords: '专家 讲座 预约 活动' },
      { id: 'route-tiantian', name: '“天天”健康教育智能体', description: '健康科普智能对话服务', route: 'tiantian', keywords: '天天 问答 智能体 健康对话' },
      { id: 'route-analytics', name: '健康素养监测分析', description: '数据治理、分析与策略任务', route: 'analytics', keywords: '监测 数据 分析 驾驶舱' },
      { id: 'record-corpus', name: '血压正常后，降压药可以自己停吗？', description: '语料编号 ZJHE-C-00862', route: 'corpus', keywords: '高血压 用药安全' },
      { id: 'record-resource', name: '一分钟看懂：血压正常后为什么不能随意停药', description: '资源编号 ZJHE-R-2026-0328', route: 'media', keywords: '高血压 短视频 老年人' },
      { id: 'record-expert', name: '王建安', description: '慢病心血管疾病管理科普分库 · 组长', route: 'experts', expertId: 'LIB-12-P001', keywords: '专家 高血压 心血管 浙江大学医学院附属第二医院' }
    ];
  }

  function renderCommandResults(query) {
    var list = $('#commandResults');
    if (!list) return;
    var q = String(query || '').trim().toLowerCase();
    var results = commandItems.filter(function (item) {
      return !q || (item.name + ' ' + item.description + ' ' + item.keywords).toLowerCase().indexOf(q) !== -1;
    }).slice(0, 8);
    commandActiveIndex = Math.min(commandActiveIndex, Math.max(0, results.length - 1));
    list.innerHTML = results.length ? results.map(function (item, index) {
      return '<button class="command-result-item ' + (index === commandActiveIndex ? 'active' : '') + '" data-command-id="' + item.id + '"><span>' + icon('search') + '</span><div><b>' + escapeHTML(item.name) + '</b><small>' + escapeHTML(item.description) + '</small></div><kbd>' + icon('corner-down-left') + '</kbd></button>';
    }).join('') : '<div class="drawer-empty"><b>未找到结果</b><small>请尝试搜索其他关键词。</small></div>';
  }

  function openCommandPalette() {
    openModal({
      kicker: 'GLOBAL SEARCH',
      title: '全局搜索',
      size: 'large',
      body: '<div class="command-search"><label class="command-search-box"><span>' + icon('search') + '</span><input class="command-search-input" id="commandSearchInput" placeholder="搜索模块、语料、资源、专家或任务"><kbd>ESC</kbd></label><div class="command-results" id="commandResults"></div></div>'
    });
    commandActiveIndex = 0;
    window.setTimeout(function () { renderCommandResults(''); }, 0);
  }

  function executeCommand(id) {
    var item = commandItems.find(function (entry) { return entry.id === id; });
    if (!item) return;
    closeModal();
    navigate(item.route);
    if (item.expertId) window.setTimeout(function () { focusExpertRecord(item.expertId); }, 0);
  }

  function filterCorpus() {
    var query = (($('#corpusSearch') || {}).value || '').trim().toLowerCase();
    var source = (($('#corpusSource') || {}).value || 'all');
    $$('.corpus-row').forEach(function (row) {
      var tag = row.getAttribute('data-tag') || '';
      var visible = (state.corpusFilter === 'all' || tag.indexOf(state.corpusFilter) !== -1) &&
        (source === 'all' || row.getAttribute('data-source') === source) && (!query || row.textContent.toLowerCase().indexOf(query) !== -1);
      row.style.display = visible ? '' : 'none';
    });
  }

  function openCollectionForm() {
    openModal({
      kicker: 'COLLECTION JOB',
      title: '新建语料采集任务',
      subtitle: '配置采集范围、关键词和执行计划。',
      size: 'large',
      body: '<div class="modal-form-grid"><label><span>任务名称</span><input value="夏季高血压健康教育专题"></label><label><span>执行策略</span><select><option>立即执行</option><option>每日 02:00</option></select></label><label class="span-2"><span>关键词</span><input value="高血压、长期用药、夏季健康管理"></label><label class="span-2"><span>来源范围</span><select><option>国家级权威来源 + 省级疾控公开资源 + 学术文献</option></select></label></div>',
      actions: [{ label: '保存并执行', action: 'run-collection', primary: true }, { label: '取消', action: 'close' }]
    });
  }

  async function runCollectionJob() {
    if (state.corpusRunning) return;
    closeModal();
    state.corpusRunning = true;
    var button = $('#runCollectionBtn');
    var status = $('#pipelineStatus');
    var steps = $$('#pipelineList .pipeline-step');
    if (button) { button.disabled = true; button.textContent = '任务执行中…'; }
    if (status) status.textContent = '采集中';
    steps.forEach(function (step, index) {
      step.classList.remove('done', 'active');
      if (index === 0) step.classList.add('active');
      var mark = $('em', step); if (mark) mark.innerHTML = icon(index === 0 ? 'loader-circle' : 'circle-minus', index === 0 ? 'spin' : '');
    });
    for (var index = 0; index < steps.length; index += 1) {
      await delay(380);
      steps.forEach(function (step, stepIndex) {
        step.classList.toggle('done', stepIndex <= index);
        step.classList.toggle('active', stepIndex === index + 1);
        var mark = $('em', step);
        if (mark) mark.innerHTML = icon(stepIndex <= index ? 'circle-check' : stepIndex === index + 1 ? 'loader-circle' : 'circle-minus', stepIndex === index + 1 ? 'spin' : '');
      });
      if (status) status.textContent = index < 3 ? ['采集中', '智能排重', '格式规范化', '自动标引'][index] : '等待人工复核';
    }
    if ($('#todayCorpusCount')) $('#todayCorpusCount').textContent = '255';
    if ($('#reviewQueueCount')) $('#reviewQueueCount').textContent = '39';
    if (button) { button.disabled = false; button.textContent = '再次执行采集'; }
    state.corpusRunning = false;
    toast('采集任务已完成，69 条有效语料进入后续处理流程。', 'success', '采集任务完成');
  }

  function openCorpusDetail(row) {
    var title = $('b', row) ? $('b', row).textContent : '语料详情';
    openModal({
      kicker: 'CORPUS RECORD',
      title: title,
      size: 'large',
      body: '<div class="detail-list"><span><small>语料编号</small><b>ZJHE-C-00862</b></span><span><small>来源层级</small><b>国家级权威来源</b></span><span><small>当前版本</small><b>V2.1</b></span><span><small>加工状态</small><b>已排重、已规范化、已标引</b></span><span><small>责任人</small><b>管理员</b></span><span><small>更新时间</small><b>' + formatDate(new Date()) + ' 14:21</b></span></div><div class="modal-note">来源、版本、加工记录和审核意见均已纳入操作审计。</div>',
      actions: [{ label: '关闭', action: 'close', primary: true }]
    });
  }

  function openCorpusReview(button) {
    var item = button.closest('.review-item');
    var title = item && $('b', item) ? $('b', item).textContent : '语料复核';
    openModal({
      kicker: 'MANUAL REVIEW',
      title: title,
      body: '<div class="review-document"><p>该条目已完成来源校验、重复检测、格式规范化和自动标引，请确认内容准确性与适用标签。</p><div class="tag-line"><em>高血压</em><em>老年健康</em><em>用药安全</em></div><label><span>审核意见</span><textarea id="corpusReviewComment">内容准确，来源清晰，同意入库。</textarea></label></div>',
      actions: [{ label: '审核并入库', action: 'approve-corpus', primary: true }, { label: '退回修改', action: 'return-corpus' }]
    });
  }

  function scriptHTML(revised) {
    var risk = revised ? '请不要自行减量或停药，应连续记录血压，并由医生结合血压记录、合并疾病及用药反应综合评估。' : '血压正常往往与规律用药和生活管理有关，不建议自行停药，可带上血压记录咨询医生。';
    return '<span class="chat-avatar ai tiantian-chat-avatar"><img src="./assets/tiantian.png" alt="天天"></span><div><small>智笔医言 · V' + state.creation.version + '</small><div class="generated-script"><div class="script-title"><span>60 秒短视频脚本</span><b>血压正常后，降压药可以自己停吗？</b></div><p><b>开场：</b>血压恢复正常了，降压药是不是就能停？先别急。</p><p><b>核心说明：</b>血压保持正常，往往正是规律用药和生活管理共同作用的结果。</p><p><b>安全提示：</b>' + risk + '</p><p><b>结尾：</b>规律监测、遵医嘱用药、按时复诊，才是长期管理血压的正确方式。</p><div class="script-meta"><span>约 152 字</span><span>数字医生：天天</span><span>社区健康教育</span><span>已关联 8 条语料</span></div></div></div>';
  }

  function renderCreationState() {
    var result = $('#scriptResult');
    if (result && state.creation.generated) result.innerHTML = scriptHTML(state.creation.revised);
    var generate = $('#generateScriptBtn');
    if (generate) generate.innerHTML = (state.creation.generated ? '重新生成' : '生成脚本') + icon('arrow-up');
    var feedback = $('#expertFeedback');
    if (feedback) feedback.classList.toggle('hidden', state.creation.status !== 'returned');
  }

  async function generateScript() {
    var result = $('#scriptResult');
    var button = $('#generateScriptBtn');
    if (button) { button.disabled = true; button.textContent = '生成中…'; }
    if (result) result.innerHTML = '<span class="chat-avatar ai tiantian-chat-avatar"><img src="./assets/tiantian.png" alt="天天"></span><div><small>智笔医言正在生成内容</small><div class="script-placeholder"><i></i><i></i><i></i></div></div>';
    await delay(800);
    state.creation.version += 1;
    state.creation.generated = true;
    state.creation.status = 'draft';
    state.creation.revised = false;
    persistState();
    renderCreationState();
    if (button) button.disabled = false;
    var chat = $('#creationChat'); if (chat) chat.scrollTop = chat.scrollHeight;
    toast('新版本脚本已生成并保存为草稿。', 'success', '内容生成完成');
  }

  function switchZheliJianjiaoTab(tab) {
    $$('.zljj-nav [data-zljj-tab]').forEach(function (button) { button.classList.toggle('active', button.getAttribute('data-zljj-tab') === tab); });
    $$('.zljj-subview').forEach(function (view) { view.classList.toggle('active', view.getAttribute('data-zljj-view') === tab); });
  }

  function openContentReview() {
    openModal({
      kicker: 'EXPERT REVIEW',
      title: '专家审核 · 血压正常后，降压药可以自己停吗？',
      size: 'large',
      body: '<div class="review-document"><div class="detail-list"><span><small>提交人</small><b>管理员</b></span><span><small>当前版本</small><b>V' + state.creation.version + '</b></span><span><small>原创度</small><b>97.8%</b></span><span><small>知识依据</small><b>8 条已复核语料</b></span></div><p>脚本结构完整、表达通俗。请确认风险提示和诊疗边界是否满足发布要求。</p><label><span>审核意见</span><textarea id="reviewOpinion">请补充不可自行停药及连续记录血压的提示。</textarea></label></div>',
      actions: [{ label: '审核通过', action: 'approve-creation', primary: true }, { label: '退回修改', action: 'return-creation' }, { label: '取消', action: 'close' }]
    });
  }

  async function runOriginality() {
    var button = $('#runOriginality');
    if (button) { button.disabled = true; button.textContent = '核验中…'; }
    await delay(650);
    var score = $('#scoreRing b'); if (score) score.textContent = '97.8%';
    var data = [['similarityCheck', '相似片段风险低'], ['citationCheck', '8 条依据均可追溯'], ['riskCheck', '风险表达符合规范'], ['boundaryCheck', '诊疗边界清晰']];
    data.forEach(function (item) {
      var node = $('#' + item[0]);
      if (node) {
        node.textContent = item[1];
        var card = node.closest('article');
        if (card) { card.classList.add('done'); var mark = $('i', card); if (mark) mark.innerHTML = icon('circle-check'); }
      }
    });
    state.creation.originality = true;
    persistState();
    if (button) { button.disabled = false; button.textContent = '重新核验'; }
    toast('原创度与风险表达核验已完成，报告已绑定当前版本。', 'success', '核验完成');
  }

  async function composeVideo() {
    openModal({ kicker: 'DIGITAL HUMAN COMPOSING', title: '正在生成数字医生视频', body: '<div class="modal-progress"><span><i id="composeProgress" style="width:12%"></i></span><b id="composeProgressText">正在准备画布与数字医生资源 12%</b></div>' });
    var frames = [[35, '正在生成数字医生口型 35%'], [62, '正在合成配音与字幕 62%'], [84, '正在进行画面质量检查 84%'], [100, '视频生成完成 100%']];
    for (var index = 0; index < frames.length; index += 1) {
      await delay(420);
      var bar = $('#composeProgress'); var label = $('#composeProgressText');
      if (bar) bar.style.width = frames[index][0] + '%';
      if (label) label.textContent = frames[index][1];
    }
    state.creation.status = 'composed';
    persistState();
    renderCreationState();
    openModal({ kicker: 'COMPOSING COMPLETE', title: '数字医生视频已生成', body: '<div class="success-panel"><span>' + icon('circle-check') + '</span><div><b>一分钟看懂：血压正常后为什么不能随意停药</b><small>00:58 · 1080×1920 · 已进入资源审核队列</small></div></div>', actions: [{ label: '进入资源库', action: 'go-media', primary: true }, { label: '关闭', action: 'close' }] });
  }

  function filterMedia() {
    var disease = (($('#mediaDisease') || {}).value || 'all');
    var type = (($('#mediaType') || {}).value || 'all');
    var audience = (($('#mediaAudience') || {}).value || 'all');
    var query = (($('#mediaSearch') || {}).value || '').trim().toLowerCase();
    var shown = 0;
    $$('#resourceGrid .resource-card').forEach(function (card) {
      var visible = (disease === 'all' || card.getAttribute('data-disease') === disease) && (type === 'all' || card.getAttribute('data-type') === type) && (audience === 'all' || card.getAttribute('data-audience') === audience) && (!query || card.textContent.toLowerCase().indexOf(query) !== -1);
      card.style.display = visible ? '' : 'none';
      if (visible) shown += 1;
    });
    if ($('#resourceEmpty')) $('#resourceEmpty').classList.toggle('hidden', shown > 0);
  }

  function openResourceUpload() {
    openModal({
      kicker: 'RESOURCE UPLOAD',
      title: '标准化上传健康科普资源',
      size: 'large',
      body: '<div class="upload-dropzone"><span>' + icon('cloud-upload') + '</span><div><b>选择或拖拽资源文件</b><small>支持 MP4、MOV、MP3、JPG、PNG、PDF，单文件不超过 2 GB</small></div><i>选择文件</i></div><div class="modal-form-grid"><label><span>资源名称</span><input value="一分钟看懂：血压正常后为什么不能随意停药"></label><label><span>内容形式</span><select><option>短视频</option><option>图文</option><option>音频</option></select></label><label><span>所属专题</span><select><option>高血压长期用药安全</option></select></label><label><span>版权来源</span><select><option>平台原创</option><option>授权使用</option></select></label></div>',
      actions: [{ label: '上传并解析', action: 'process-resource', primary: true }, { label: '取消', action: 'close' }]
    });
  }

  async function processResource() {
    openModal({ kicker: 'RESOURCE PROCESSING', title: '正在上传并解析资源', body: '<div class="modal-progress"><span><i id="resourceProgress" style="width:18%"></i></span><b id="resourceProgressText">文件完整性校验 18%</b></div>' });
    var frames = [[43, '媒体结构解析 43%'], [68, '疾病与人群标签识别 68%'], [88, '版权与元数据检查 88%'], [100, '资源处理完成 100%']];
    for (var index = 0; index < frames.length; index += 1) {
      await delay(380);
      if ($('#resourceProgress')) $('#resourceProgress').style.width = frames[index][0] + '%';
      if ($('#resourceProgressText')) $('#resourceProgressText').textContent = frames[index][1];
    }
    openModal({ kicker: 'SMART TAGGING', title: '资源已完成解析与标引', body: '<div class="detail-list"><span><small>资源编号</small><b>ZJHE-R-2026-0331</b></span><span><small>疾病领域</small><b>高血压 · 慢病管理</b></span><span><small>目标人群</small><b>60 岁以上人群</b></span><span><small>资源状态</small><b>待提交审核</b></span></div><div class="tag-line"><em>高血压</em><em>用药安全</em><em>老年人</em><em>短视频</em></div>', actions: [{ label: '保存并提交审核', action: 'save-resource', primary: true }, { label: '暂存草稿', action: 'close' }] });
  }

  function openResourceDetail(card) {
    var title = $('h3', card) ? $('h3', card).textContent : '资源详情';
    openModal({ kicker: 'RESOURCE DETAIL', title: title, size: 'large', body: '<div class="detail-list"><span><small>资源编号</small><b>' + escapeHTML(($('small', $('.resource-meta', card)) || {}).textContent || 'ZJHE-R-2026-0328') + '</b></span><span><small>版权来源</small><b>平台原创</b></span><span><small>授权范围</small><b>浙江省健康教育服务体系</b></span><span><small>审核状态</small><b>已审核</b></span><span><small>发布渠道</small><b>省级资源库、智能体知识卡、基层素材包</b></span><span><small>累计调用</small><b>1,286 次</b></span></div>', actions: [{ label: '发布配置', action: 'open-publish', primary: true }, { label: '关闭', action: 'close' }] });
  }

  function openPublish() {
    openModal({ kicker: 'CHANNEL PUBLISHING', title: '发布资源', body: '<div class="publish-options"><label><input type="checkbox" checked><span>省级健康科普资源库</span></label><label><input type="checkbox" checked><span>“天天”智能体知识卡</span></label><label><input type="checkbox" checked><span>基层活动素材包</span></label><label><input type="checkbox"><span>机构内容门户</span></label></div><label><span>发布说明</span><textarea>高血压长期用药安全专题核心资源。</textarea></label>', actions: [{ label: '确认发布', action: 'publish-resource', primary: true }, { label: '取消', action: 'close' }] });
  }

  function buildExpertRecords() {
    expertDirectory = window.ZJ_EXPERT_LIBRARY || expertDirectory;
    expertRecords = [];
    (expertDirectory.libraries || []).forEach(function (library, libraryIndex) {
      (library.people || []).forEach(function (person, personIndex) {
        expertRecords.push({
          id: person.id,
          name: person.name,
          roles: Array.isArray(person.roles) ? person.roles : ['成员'],
          libraryId: library.id,
          libraryName: library.name,
          leadUnit: library.leadUnit,
          libraryIndex: libraryIndex + 1,
          sequence: personIndex + 1
        });
      });
    });
  }

  function selectedExpertRecord() {
    return expertRecords.find(function (record) { return record.id === state.selectedExpertId; }) || expertRecords[0] || null;
  }

  function roleBadgeHTML(roles) {
    return roles.map(function (role) {
      var roleClass = role === '组长' ? 'leader' : role === '副组长' ? 'deputy' : role === '联络员' ? 'liaison' : 'member';
      return '<span class="expert-role-badge ' + roleClass + '">' + escapeHTML(role) + '</span>';
    }).join('');
  }

  function renderExpertFilters() {
    var select = $('#expertLibrary');
    if (!select || select.getAttribute('data-ready') === 'true') return;
    select.innerHTML = '<option value="all">全部 24 个分库</option>' + (expertDirectory.libraries || []).map(function (library) {
      return '<option value="' + escapeHTML(library.id) + '">' + escapeHTML(library.name) + '（' + library.count + '人）</option>';
    }).join('');
    select.setAttribute('data-ready', 'true');
  }

  function currentExpertFilters() {
    return {
      query: (($('#expertSearch') || {}).value || '').trim().toLowerCase(),
      library: (($('#expertLibrary') || {}).value || 'all'),
      role: (($('#expertRole') || {}).value || 'all')
    };
  }

  function expertMatches(record, filters) {
    var searchable = [record.name, record.libraryName, record.leadUnit, record.roles.join(' ')].join(' ').toLowerCase();
    return (filters.library === 'all' || record.libraryId === filters.library) &&
      (filters.role === 'all' || record.roles.indexOf(filters.role) !== -1) &&
      (!filters.query || searchable.indexOf(filters.query) !== -1);
  }

  function expertCardHTML(record) {
    var selected = record.id === state.selectedExpertId;
    return '<article class="expert-card ' + (selected ? 'selected' : '') + '" data-expert-id="' + escapeHTML(record.id) + '">' +
      '<div class="expert-card-head"><span class="expert-avatar">' + icon('user-round') + '</span><div><h3>' + escapeHTML(record.name) + '</h3><div class="expert-role-row">' + roleBadgeHTML(record.roles) + '</div></div></div>' +
      '<div class="expert-card-body"><p>' + icon('users-round') + '<span>' + escapeHTML(record.libraryName) + '</span></p><small>' + icon('landmark') + '<span>分库牵头单位：' + escapeHTML(record.leadUnit) + '</span></small></div>' +
      '<div class="expert-card-foot"><span>分库名录第 ' + record.sequence + ' 位</span><button class="select-expert" data-expert-id="' + escapeHTML(record.id) + '">' + (selected ? '当前查看' : '查看成员') + icon('arrow-right') + '</button></div></article>';
  }

  function renderExpertPagination(totalPages) {
    var pagination = $('#expertPagination');
    if (!pagination) return;
    if (totalPages <= 1) {
      pagination.innerHTML = '';
      pagination.classList.add('hidden');
      return;
    }
    pagination.classList.remove('hidden');
    var start = Math.max(1, expertPage - 2);
    var end = Math.min(totalPages, start + 4);
    start = Math.max(1, end - 4);
    var pages = [];
    for (var page = start; page <= end; page += 1) pages.push(page);
    pagination.innerHTML = '<button data-expert-page="prev" ' + (expertPage === 1 ? 'disabled' : '') + ' aria-label="上一页">' + icon('chevron-left') + '</button>' +
      (start > 1 ? '<button data-expert-page="1">1</button><span>…</span>' : '') +
      pages.map(function (pageNumber) { return '<button data-expert-page="' + pageNumber + '" class="' + (pageNumber === expertPage ? 'active' : '') + '">' + pageNumber + '</button>'; }).join('') +
      (end < totalPages ? '<span>…</span><button data-expert-page="' + totalPages + '">' + totalPages + '</button>' : '') +
      '<button data-expert-page="next" ' + (expertPage === totalPages ? 'disabled' : '') + ' aria-label="下一页">' + icon('chevron-right') + '</button>';
  }

  function updateExpertDetail() {
    var record = selectedExpertRecord();
    if (!record) return;
    if ($('#selectedExpertName')) $('#selectedExpertName').textContent = record.name;
    if ($('#selectedExpertRole')) $('#selectedExpertRole').textContent = record.roles.join(' / ');
    if ($('#selectedExpertLibraryName')) $('#selectedExpertLibraryName').textContent = record.libraryName;
    if ($('#selectedExpertLibrary')) $('#selectedExpertLibrary').textContent = record.libraryName;
    if ($('#selectedExpertRoleDetail')) $('#selectedExpertRoleDetail').textContent = record.roles.join(' / ');
    if ($('#selectedExpertLeadUnit')) $('#selectedExpertLeadUnit').textContent = record.leadUnit;
    if ($('#selectedExpertIndex')) $('#selectedExpertIndex').textContent = '第 ' + record.sequence + ' 位（按官方名录顺序）';
    if ($('#selectedExpertOrdinal')) $('#selectedExpertOrdinal').textContent = '第 ' + record.sequence + ' 位（按官方名录顺序）';
    var badge = $('#selectedExpertLibraryBadge');
    if (badge) badge.textContent = record.libraryName;
  }

  function renderExpertDirectory() {
    var cards = $('#expertCards');
    if (!cards) return;
    var filters = currentExpertFilters();
    expertFilteredRecords = expertRecords.filter(function (record) { return expertMatches(record, filters); });
    var totalPages = Math.max(1, Math.ceil(expertFilteredRecords.length / expertPageSize));
    expertPage = Math.min(Math.max(1, expertPage), totalPages);
    var start = (expertPage - 1) * expertPageSize;
    var pageRecords = expertFilteredRecords.slice(start, start + expertPageSize);
    cards.innerHTML = pageRecords.length ? pageRecords.map(expertCardHTML).join('') : '<div class="expert-empty-state">' + icon('search') + '<b>未找到符合条件的成员</b><small>请调整姓名、分库、牵头单位或角色筛选条件。</small></div>';
    if ($('#expertResultCount')) $('#expertResultCount').textContent = '共 ' + expertFilteredRecords.length.toLocaleString('zh-CN') + ' 名成员 · 第 ' + expertPage + ' / ' + totalPages + ' 页';
    renderExpertPagination(totalPages);
    updateExpertDetail();
  }

  function filterExperts(resetPage) {
    if (resetPage !== false) expertPage = 1;
    renderExpertDirectory();
  }

  function selectExpertById(id) {
    var record = expertRecords.find(function (item) { return item.id === id; });
    if (!record) return;
    state.selectedExpertId = record.id;
    $$('#expertCards .expert-card').forEach(function (card) {
      var selected = card.getAttribute('data-expert-id') === record.id;
      card.classList.toggle('selected', selected);
      var button = $('.select-expert', card);
      if (button) button.innerHTML = (selected ? '当前查看' : '查看成员') + icon('arrow-right');
    });
    updateExpertDetail();
  }

  function focusExpertRecord(id) {
    var record = expertRecords.find(function (item) { return item.id === id; });
    if (!record) return;
    state.selectedExpertId = record.id;
    var librarySelect = $('#expertLibrary');
    var roleSelect = $('#expertRole');
    var searchInput = $('#expertSearch');
    if (librarySelect) librarySelect.value = record.libraryId;
    if (roleSelect) roleSelect.value = 'all';
    if (searchInput) searchInput.value = record.name;
    expertPage = 1;
    renderExpertDirectory();
  }

  function resetExpertFilters() {
    if ($('#expertSearch')) $('#expertSearch').value = '';
    if ($('#expertLibrary')) $('#expertLibrary').value = 'all';
    if ($('#expertRole')) $('#expertRole').value = 'all';
    expertPage = 1;
    renderExpertDirectory();
  }

  function changeExpertPage(target) {
    var totalPages = Math.max(1, Math.ceil(expertFilteredRecords.length / expertPageSize));
    var nextPage = target === 'prev' ? expertPage - 1 : target === 'next' ? expertPage + 1 : Number(target);
    if (!Number.isFinite(nextPage)) return;
    expertPage = Math.min(totalPages, Math.max(1, nextPage));
    renderExpertDirectory();
    var cards = $('#expertCards');
    if (cards && cards.scrollIntoView) cards.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function initExpertDirectory() {
    buildExpertRecords();
    renderExpertFilters();
    if (!expertRecords.length) return;
    if (!expertRecords.some(function (record) { return record.id === state.selectedExpertId; })) state.selectedExpertId = expertRecords[0].id;
    renderExpertDirectory();
  }

  function openAppointment() {
    var record = selectedExpertRecord();
    if (!record) {
      toast('专家名录尚未完成加载，请刷新页面后重试。', 'warning', '暂时无法预约');
      return;
    }
    var appointmentDate = new Date();
    appointmentDate.setDate(appointmentDate.getDate() + 7);
    openModal({
      kicker: 'EXPERT APPOINTMENT',
      title: '发起专家讲座预约',
      subtitle: '预约对象来自浙江省健康科普专家库官方分库成员名单。',
      size: 'large',
      body: '<div class="appointment-steps"><span class="active"><i>1</i>活动信息</span><span><i>2</i>时间确认</span><span><i>3</i>提交预约</span></div>' +
        '<div class="appointment-expert-summary"><span>' + icon('user-check') + '</span><div><small>预约成员</small><b>' + escapeHTML(record.name) + '</b><p>' + escapeHTML(record.roles.join(' / ')) + ' · ' + escapeHTML(record.libraryName) + '</p></div></div>' +
        '<div class="modal-form-grid"><label><span>分库牵头单位</span><input value="' + escapeHTML(record.leadUnit) + '" readonly></label><label><span>活动形式</span><select><option>线下讲座</option><option>线上直播</option><option>健康咨询活动</option></select></label><label class="span-2"><span>活动主题</span><input value="' + escapeHTML(record.libraryName.replace('科普分库', '健康科普讲座')) + '"></label><label><span>活动日期</span><input type="date" value="' + formatDate(appointmentDate) + '"></label><label><span>开始时间</span><input type="time" value="14:30"></label><label class="span-2"><span>服务单位</span><input value="基层健康教育服务单位"></label><label><span>预计人数</span><input type="number" value="80" min="1"></label><label><span>联系人</span><input value="管理员"></label></div>',
      actions: [{ label: '提交预约', action: 'submit-appointment', primary: true }, { label: '取消', action: 'close' }]
    });
  }

  function activityLedgerHTML() {
    var record = selectedExpertRecord();
    var name = record ? record.name : '待确认成员';
    var library = record ? record.libraryName : '健康科普分库';
    return '<div class="drawer-stat-grid"><div><small>本月活动</small><b>42</b></div><div><small>待归档</small><b>5</b></div></div><div class="task-list"><article class="task-item"><span class="task-priority high">进行</span><div><b>' + escapeHTML(library.replace('科普分库', '健康科普讲座')) + '</b><small class="task-meta">活动排期确认中 · ' + escapeHTML(name) + '</small></div><div class="task-action"><button>查看</button></div></article><article class="task-item"><span class="task-priority low">完成</span><div><b>夏季科学补水健康课堂</b><small class="task-meta">签到 126 人 · 满意度 4.93</small></div><div class="task-action"><button>归档</button></div></article></div>';
  }

  function answerFor(question) {
    if (/补水|喝水/.test(question)) return '夏季补水应少量多次，根据活动量、出汗情况和基础疾病合理调整。老年人、儿童及心肾功能异常人群应遵循专业人员建议，避免短时间大量饮水。';
    if (/血压|降压药|停药/.test(question)) return '不建议自行停药。血压保持正常往往与规律用药和生活管理有关，是否减量或停药，应由医生结合连续血压记录、合并疾病及用药反应综合评估。如出现胸痛、呼吸困难或剧烈头痛等不适，请及时就医。';
    return '请结合具体健康状况持续观察，并优先参考已审核的健康科普内容。如涉及症状变化、用药调整或诊疗决策，建议及时咨询专业人员。';
  }

  async function sendQuestion() {
    if (state.chatBusy) return;
    var input = $('#assistantInput');
    var messages = $('#assistantMessages');
    var character = $('#tiantianCharacterStage');
    var question = input ? input.value.trim() : '';
    if (!question || !messages) return;
    state.chatBusy = true;
    if (character) character.classList.add('thinking');
    messages.insertAdjacentHTML('beforeend', '<div class="assistant-message user"><span>' + icon('user-round') + '</span><div><p>' + escapeHTML(question) + '</p><small>' + formatTime(new Date()) + '</small></div></div><div class="assistant-message bot typing" id="tiantianTyping"><div class="tiantian-avatar"><img src="./assets/tiantian.png" alt="天天"></div><div><p>正在检索审核后知识内容<span class="typing-dots">...</span></p></div></div>');
    if (input) input.value = '';
    messages.scrollTop = messages.scrollHeight;
    await delay(650);
    var typing = $('#tiantianTyping'); if (typing) typing.remove();
    messages.insertAdjacentHTML('beforeend', '<div class="assistant-message bot"><div class="tiantian-avatar"><img src="./assets/tiantian.png" alt="天天"></div><div><b>天天</b><p>' + escapeHTML(answerFor(question)) + '</p><small>健康科普信息不能替代医生诊断和治疗。</small></div></div>');
    if (character) {
      character.classList.remove('thinking');
      character.classList.add('speaking');
      window.setTimeout(function () { character.classList.remove('speaking'); }, 1200);
    }
    state.chatBusy = false;
    messages.scrollTop = messages.scrollHeight;
  }

  function newConversation() {
    var messages = $('#assistantMessages');
    if (!messages) return;
    messages.innerHTML = '<div class="assistant-welcome dialogue-welcome"><div><small>TIANTIAN HEALTH EDUCATION</small><h2>今天想了解什么健康知识？</h2><p>我会用通俗易懂的方式提供健康科普信息。涉及症状、用药调整或诊疗决策时，请及时咨询专业医务人员。</p><div class="quick-questions"><button>血压正常了，降压药可以停吗？</button><button>在家怎样记录血压更准确？</button><button>高血压人群夏季运动要注意什么？</button></div></div></div>';
    if ($('#assistantInput')) $('#assistantInput').value = '';
    var character = $('#tiantianCharacterStage');
    if (character) character.classList.remove('thinking', 'speaking');
  }

  function updateAnalytics() {
    var select = $('#populationFilter');
    state.population = select ? select.value : 'all';
    var values = {
      all: { rate: '34.6', text: '65 岁以上人群在“慢病用药安全”维度相对薄弱，建议优先配置短视频、社区讲座和智能问答服务。' },
      elderly: { rate: '28.9', text: '65 岁以上人群的慢病用药安全与健康信息辨识水平相对较弱，建议采用大字版短视频、社区讲座和可追溯问答协同干预。' },
      youth: { rate: '41.2', text: '18—34 岁人群整体素养水平较高，健康信息甄别与规律生活方式仍可作为移动端科普重点。' }
    };
    var current = values[state.population] || values.all;
    if ($('#literacyRate')) $('#literacyRate').innerHTML = current.rate + '<em>%</em>';
    if ($('#insightText')) $('#insightText').textContent = current.text;
    window.requestAnimationFrame(function () { drawTrendChart(); drawZhejiangMap(); });
  }

  var zhejiangCityMetrics = [
    { name: '杭州市', value: 38.4, samples: 1050 },
    { name: '宁波市', value: 37.6, samples: 920 },
    { name: '温州市', value: 34.5, samples: 980 },
    { name: '嘉兴市', value: 37.2, samples: 680 },
    { name: '湖州市', value: 36.8, samples: 560 },
    { name: '绍兴市', value: 35.9, samples: 720 },
    { name: '金华市', value: 33.8, samples: 810 },
    { name: '衢州市', value: 30.9, samples: 590 },
    { name: '舟山市', value: 35.4, samples: 430 },
    { name: '台州市', value: 33.1, samples: 840 },
    { name: '丽水市', value: 29.8, samples: 1020 }
  ];

  function analyticsCityData() {
    var offset = state.population === 'elderly' ? -5.7 : state.population === 'youth' ? 6.6 : 0;
    return zhejiangCityMetrics.map(function (item) {
      return { name: item.name, value: Math.max(18, Math.min(50, item.value + offset)), samples: item.samples };
    });
  }

  function cityMapColor(value) {
    if (value >= 35) return '#087f78';
    if (value >= 32) return '#0b6892';
    return '#9a5a12';
  }

  function drawZhejiangMap() {
    var container = $('#zhejiangMapChart');
    if (!container || state.route !== 'analytics') return;
    if (!window.echarts || !window.ZHEJIANG_CITIES_GEOJSON) {
      container.innerHTML = '<div class="map-load-error">地图资源加载失败，请重新打开页面</div>';
      return;
    }
    if (!zhejiangMapRegistered) {
      window.echarts.registerMap('zhejiang-cities', window.ZHEJIANG_CITIES_GEOJSON);
      zhejiangMapRegistered = true;
    }
    if (!zhejiangMapChart) zhejiangMapChart = window.echarts.init(container, null, { renderer: 'canvas' });
    var cityData = analyticsCityData();
    var valueByName = {};
    cityData.forEach(function (item) { valueByName[item.name] = item; });
    var features = window.ZHEJIANG_CITIES_GEOJSON.features || [];
    var monitorPoints = features.map(function (feature) {
      var item = valueByName[feature.properties.name];
      var coordinate = feature.properties.centroid || feature.properties.center;
      return { name: feature.properties.name, value: [coordinate[0], coordinate[1], item ? item.value : 0] };
    });
    zhejiangMapChart.setOption({
      animationDurationUpdate: 420,
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        confine: true,
        padding: [8, 10],
        backgroundColor: 'rgba(3,24,48,.96)',
        borderColor: 'rgba(66,217,255,.55)',
        textStyle: { color: '#dffaff', fontSize: 11 },
        extraCssText: 'box-shadow:0 0 18px rgba(66,217,255,.18);',
        formatter: function (params) {
          var item = valueByName[params.name];
          if (!item) return params.name || '';
          return '<b style="color:#fff">' + item.name.replace('市', '') + '</b><br>健康素养水平　<span style="color:#42d9ff">' + item.value.toFixed(1) + '%</span><br>有效监测样本　' + item.samples.toLocaleString('zh-CN') + ' 份';
        }
      },
      geo: {
        map: 'zhejiang-cities',
        roam: false,
        top: 5,
        right: 16,
        bottom: 32,
        left: 58,
        selectedMode: 'single',
        itemStyle: {
          areaColor: '#0b5277',
          borderColor: '#78e6ff',
          borderWidth: 1,
          shadowColor: 'rgba(50,208,255,.55)',
          shadowBlur: 11
        },
        label: {
          show: true,
          color: '#e6fbff',
          formatter: function (params) {
            var item = valueByName[params.name];
            return item ? '{city|' + params.name.replace('市', '') + '}\n{value|' + item.value.toFixed(1) + '%}' : params.name;
          },
          rich: {
            city: { color: '#effdff', fontSize: 9, fontWeight: 700, lineHeight: 13, textShadowColor: '#00223c', textShadowBlur: 4 },
            value: { color: '#79e8ff', fontSize: 8, lineHeight: 11 }
          }
        },
        emphasis: {
          label: { show: true, color: '#fff' },
          itemStyle: { areaColor: '#109caa', borderColor: '#fff', borderWidth: 1.5, shadowColor: '#42d9ff', shadowBlur: 22 }
        },
        select: {
          label: { color: '#fff' },
          itemStyle: { areaColor: '#0aa5a3', borderColor: '#fff', borderWidth: 1.5, shadowColor: '#42d9ff', shadowBlur: 24 }
        },
        regions: cityData.map(function (item) {
          return { name: item.name, itemStyle: { areaColor: cityMapColor(item.value) } };
        })
      },
      series: [{
        name: '监测点',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: monitorPoints,
        symbolSize: 5,
        showEffectOn: 'render',
        rippleEffect: { period: 4, scale: 3.5, brushType: 'stroke' },
        itemStyle: { color: '#6fffe3', shadowColor: '#6fffe3', shadowBlur: 8 },
        emphasis: { scale: 1.5 },
        silent: true,
        zlevel: 2
      }]
    }, true);
    zhejiangMapChart.off('click');
    zhejiangMapChart.on('click', function (params) {
      var item = valueByName[params.name];
      if (!item) return;
      if ($('#insightText')) $('#insightText').textContent = item.name.replace('市', '') + '当前健康素养水平为 ' + item.value.toFixed(1) + '%，建议结合人群结构与薄弱维度制定区域科普任务。';
    });
    zhejiangMapChart.resize();
  }

  function chartSeries() {
    var sets = {
      overview: { labels: ['2022', '2023', '2024', '2025', '2026'], primary: [28.3, 30.1, 31.7, 32.3, 34.6], secondary: [22.1, 23.8, 25.2, 26.6, 28.9] },
      quality: { labels: ['完整性', '一致性', '有效性', '及时性', '可追溯'], primary: [46.2, 43.8, 39.7, 35.9, 41.8], secondary: [41.8, 38.6, 35.2, 31.1, 36.9] },
      questionnaire: { labels: ['基础知识', '健康行为', '基本技能', '用药安全', '信息辨识'], primary: [39.8, 37.2, 35.1, 26.8, 33.4], secondary: [32.7, 30.4, 28.8, 21.9, 25.6] },
      trend: { labels: ['Q1', 'Q2', 'Q3', 'Q4', '年度'], primary: [32.8, 33.2, 33.9, 34.3, 34.6], secondary: [26.9, 27.4, 27.8, 28.4, 28.9] }
    };
    return sets[state.analyticsTab] || sets.overview;
  }

  function drawTrendChart() {
    var canvas = $('#trendChart');
    if (!canvas || state.route !== 'analytics') return;
    var parent = canvas.parentElement;
    var width = Math.max(240, parent ? parent.clientWidth : 420);
    var height = Math.max(118, parent ? parent.clientHeight : 180);
    var dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    var ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);
    var compact = width < 360 || height < 190;
    var padding = compact ? { left: 30, right: 10, top: 10, bottom: 24 } : { left: 46, right: 22, top: 24, bottom: 38 };
    var innerWidth = width - padding.left - padding.right;
    var innerHeight = height - padding.top - padding.bottom;
    var data = chartSeries();
    var min = 18; var max = 50;
    var x = function (index) { return padding.left + index * innerWidth / (data.labels.length - 1); };
    var y = function (value) { return padding.top + (max - value) / (max - min) * innerHeight; };
    ctx.font = (compact ? '7px' : '12px') + ' "Microsoft YaHei", sans-serif';
    ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
    [20, 30, 40, 50].forEach(function (tick) {
      var py = y(tick); ctx.strokeStyle = 'rgba(77,185,255,.14)'; ctx.beginPath(); ctx.moveTo(padding.left, py); ctx.lineTo(width - padding.right, py); ctx.stroke(); ctx.fillStyle = '#5f9fba'; ctx.fillText(tick + '%', padding.left - 4, py);
    });
    ctx.textAlign = 'center'; ctx.textBaseline = 'top';
    data.labels.forEach(function (label, index) { ctx.fillStyle = '#68aac3'; ctx.fillText(label, x(index), height - padding.bottom + 7); });
    function line(values, color, fill) {
      var gradient = ctx.createLinearGradient(0, padding.top, 0, height - padding.bottom); gradient.addColorStop(0, fill); gradient.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.beginPath(); values.forEach(function (value, index) { if (!index) ctx.moveTo(x(index), y(value)); else ctx.lineTo(x(index), y(value)); }); ctx.lineTo(x(values.length - 1), height - padding.bottom); ctx.lineTo(x(0), height - padding.bottom); ctx.closePath(); ctx.fillStyle = gradient; ctx.fill();
      ctx.beginPath(); values.forEach(function (value, index) { if (!index) ctx.moveTo(x(index), y(value)); else ctx.lineTo(x(index), y(value)); }); ctx.strokeStyle = color; ctx.lineWidth = compact ? 1.6 : 3; ctx.stroke();
      values.forEach(function (value, index) { ctx.beginPath(); ctx.arc(x(index), y(value), compact ? 2.3 : 4.5, 0, Math.PI * 2); ctx.fillStyle = '#071b35'; ctx.fill(); ctx.strokeStyle = color; ctx.stroke(); });
    }
    line(data.secondary, '#8b5cf6', 'rgba(139,92,246,.12)');
    line(data.primary, '#42d9ff', 'rgba(66,217,255,.16)');
    state.chartPoints = data.labels.map(function (label, index) { return { x: x(index), label: label, primary: data.primary[index], secondary: data.secondary[index] }; });
  }

  function openSurveyUpload() {
    openModal({ kicker: 'DATA IMPORT', title: '上传居民健康素养监测数据', size: 'large', body: '<div class="upload-dropzone"><span>' + icon('file-spreadsheet') + '</span><div><b>选择监测数据文件</b><small>支持 XLSX、CSV，需符合居民健康素养问卷数据模板</small></div><i>选择文件</i></div><div class="modal-form-grid"><label><span>数据年度</span><select><option>2026</option><option>2025</option></select></label><label><span>监测范围</span><select><option>浙江省</option></select></label><label><span>数据批次</span><input value="ZJHL-2026-Q3-04"></label><label><span>字段模板</span><select><option>居民健康素养问卷 V3.2</option></select></label></div>', actions: [{ label: '上传并校验', action: 'validate-survey', primary: true }, { label: '取消', action: 'close' }] });
  }

  async function validateSurvey() {
    openModal({ kicker: 'DATA VALIDATION', title: '正在执行数据校验', body: '<div class="modal-progress"><span><i id="surveyProgress" style="width:20%"></i></span><b id="surveyProgressText">字段完整性校验 20%</b></div>' });
    var frames = [[46, '取值范围校验 46%'], [72, '跨题逻辑校验 72%'], [100, '质量报告生成 100%']];
    for (var index = 0; index < frames.length; index += 1) { await delay(400); if ($('#surveyProgress')) $('#surveyProgress').style.width = frames[index][0] + '%'; if ($('#surveyProgressText')) $('#surveyProgressText').textContent = frames[index][1]; }
    openModal({ kicker: 'VALIDATION REPORT', title: '数据校验完成', subtitle: '原始记录 8,642 条', body: '<div class="validation-grid"><article><span>缺失项</span><b>7</b><small>需补全或剔除</small></article><article><span>数值越界</span><b>11</b><small>年龄或编码异常</small></article><article><span>逻辑冲突</span><b>5</b><small>答案前后不一致</small></article><article class="ok"><span>结构通过</span><b>' + icon('badge-check') + '</b><small>字段与模板匹配</small></article></div>', actions: [{ label: '执行数据清洗', action: 'clean-survey', primary: true }, { label: '下载错误明细', action: 'close' }] });
  }

  async function cleanSurvey() {
    openModal({ kicker: 'DATA CLEANING', title: '正在执行数据清洗', body: '<div class="modal-progress"><span><i id="surveyCleanProgress" style="width:18%"></i></span><b id="surveyCleanText">缺失值规则处理 18%</b></div>' });
    var frames = [[45, '异常取值修正 45%'], [73, '逻辑冲突记录处理 73%'], [100, '有效样本生成 100%']];
    for (var index = 0; index < frames.length; index += 1) { await delay(400); if ($('#surveyCleanProgress')) $('#surveyCleanProgress').style.width = frames[index][0] + '%'; if ($('#surveyCleanText')) $('#surveyCleanText').textContent = frames[index][1]; }
    openModal({ kicker: 'CLEANING COMPLETE', title: '数据清洗完成', body: '<div class="success-panel"><span>' + icon('circle-check') + '</span><div><b>形成 8,600 条有效样本</b><small>原始 8,642 条 · 排除 42 条 · 完整率 99.3%</small></div></div>', actions: [{ label: '进入分析', action: 'close', primary: true }] });
  }

  function openTopicTask() {
    openModal({ kicker: 'STRATEGY TASK', title: '创建精准科普任务', body: '<div class="detail-list"><span><small>任务来源</small><b>健康素养监测洞察 HL-2026-0716-03</b></span><span><small>目标人群</small><b>65 岁以上人群</b></span><span><small>重点主题</small><b>高血压长期用药安全</b></span><span><small>建议形式</small><b>短视频 + 社区讲座 + 智能问答</b></span></div><label><span>任务负责人</span><select><option>管理员</option></select></label>', actions: [{ label: '创建任务', action: 'create-topic-task', primary: true }, { label: '取消', action: 'close' }] });
  }

  function handleModalAction(action) {
    if (action === 'close') closeModal();
    else if (action === 'run-collection') runCollectionJob();
    else if (action === 'approve-corpus') { closeModal(); toast('语料已审核入库并生成审计记录。', 'success'); }
    else if (action === 'return-corpus') { closeModal(); toast('语料已退回加工队列。', 'warning', '已退回'); }
    else if (action === 'approve-creation') { state.creation.status = 'approved'; persistState(); closeModal(); renderCreationState(); switchZheliJianjiaoTab('composer'); toast('内容审核通过，可进入数字医生合成。', 'success', '审核通过'); }
    else if (action === 'return-creation') { state.creation.status = 'returned'; state.creation.revised = true; persistState(); closeModal(); renderCreationState(); switchZheliJianjiaoTab('writer'); toast('内容已退回作者修改。', 'warning', '已退回'); }
    else if (action === 'go-media') { closeModal(); navigate('media'); }
    else if (action === 'process-resource') processResource();
    else if (action === 'save-resource') { closeModal(); toast('资源已保存并提交审核。', 'success'); }
    else if (action === 'open-publish') openPublish();
    else if (action === 'publish-resource') { closeModal(); toast('发布任务已创建，可在发布任务栏目查看进度。', 'success', '发布任务已创建'); }
    else if (action === 'submit-appointment') { closeModal(); toast('预约已提交，等待专家确认。', 'success', '预约已提交'); }
    else if (action === 'validate-survey') validateSurvey();
    else if (action === 'clean-survey') cleanSurvey();
    else if (action === 'create-topic-task') { closeModal(); state.creation.status = 'draft'; state.creation.generated = true; state.creation.revised = false; persistState(); renderCreationState(); navigate('creation'); toast('科普任务已创建并进入内容工作台。', 'success', '任务已创建'); }
  }

  async function toggleFullscreen() {
    try {
      if (!document.fullscreenElement) await document.documentElement.requestFullscreen();
      else await document.exitFullscreen();
    } catch (error) {
      toast('浏览器未允许切换全屏。', 'warning');
    }
  }

  function updateFullscreenButton() {
    var button = $('#fullscreenBtn');
    if (button) {
      button.innerHTML = icon('maximize-2');
      button.title = document.fullscreenElement ? '退出全屏' : '切换全屏';
      button.setAttribute('aria-label', button.title);
    }
  }

  function toggleUserMenu() {
    var menu = $('#userPopover');
    if (menu) menu.classList.toggle('hidden');
  }

  function closeUserMenu() {
    if ($('#userPopover')) $('#userPopover').classList.add('hidden');
  }

  function openUserSection(title, rows) {
    closeUserMenu();
    openDrawer('user', title, 'ACCOUNT CENTER', '<div class="settings-list">' + rows.map(function (row) { return '<article><div><b>' + row[0] + '</b><small>' + row[1] + '</small></div><button>管理</button></article>'; }).join('') + '</div>');
  }

  function handleDocumentClick(event) {
    var expertCardTarget = event.target.closest('.expert-card[data-expert-id]');
    var button = event.target.closest('button, [data-route-target]');
    if (!button && expertCardTarget) {
      selectExpertById(expertCardTarget.getAttribute('data-expert-id'));
      return;
    }
    if (!button) {
      if (!event.target.closest('#userPopover, #userMenuBtn')) closeUserMenu();
      return;
    }
    var routeTarget = button.getAttribute('data-route-target');
    if (routeTarget) { navigate(routeTarget); return; }
    if (button.classList.contains('nav-item')) { navigate(button.getAttribute('data-route')); return; }
    if (button.id === 'mobileMenu') { $('#appShell').classList.toggle('nav-open'); return; }
    if (button.id === 'tenantSwitcher') { openDrawer('tenant', '切换组织', 'TENANT CONTEXT', tenantDrawerHTML()); return; }
    if (button.id === 'globalSearchBtn') { openCommandPalette(); return; }
    if (button.id === 'workQueueBtn' || button.id === 'viewAllTasks') { openDrawer('tasks', '待办中心', 'MY WORK QUEUE', taskDrawerHTML()); return; }
    if (button.id === 'notificationBtn') { openDrawer('notifications', '消息中心', 'NOTIFICATIONS', notificationDrawerHTML()); return; }
    if (button.id === 'auditCenterBtn') { openDrawer('audit', '操作审计', 'AUDIT LOG', auditDrawerHTML()); return; }
    if (button.id === 'systemSettingsBtn') { openDrawer('settings', '系统设置', 'PLATFORM GOVERNANCE', settingsDrawerHTML()); return; }
    if (button.id === 'drawerClose') { closeDrawer(); return; }
    if (button.id === 'modalClose') { closeModal(); return; }
    if (button.id === 'userMenuBtn') { toggleUserMenu(); return; }
    if (button.id === 'fullscreenBtn') { toggleFullscreen(); return; }
    if (button.id === 'profileSettings') { openUserSection('个人设置', [['基本资料', '姓名、头像与联系方式'], ['通知偏好', '消息渠道与免打扰时间']]); return; }
    if (button.id === 'organizationSettings') { openUserSection('组织与成员', [['当前组织', currentTenant().name], ['角色权限', '省级运营管理员']]); return; }
    if (button.id === 'securityCenter') { openUserSection('安全中心', [['登录安全', '最近登录设备与会话管理'], ['账号保护', '多因素认证与密码策略']]); return; }
    if (button.id === 'signOut') { closeUserMenu(); openModal({ kicker: 'ACCOUNT SESSION', title: '退出当前账号？', body: '<div class="modal-note">退出后需要重新完成身份认证才能进入系统。</div>', actions: [{ label: '取消', action: 'close' }, { label: '确认退出', action: 'close', primary: true }] }); return; }
    if (button.hasAttribute('data-tenant-id')) { state.tenantId = button.getAttribute('data-tenant-id'); persistState(); updateTenantUI(); closeDrawer(); toast('组织已切换为“' + currentTenant().name + '”。', 'success', '组织切换成功'); return; }
    if (button.hasAttribute('data-task-route')) { var taskRoute = button.getAttribute('data-task-route'); closeDrawer(); navigate(taskRoute); return; }
    if (button.hasAttribute('data-task-done')) { var taskId = button.getAttribute('data-task-done'); var task = state.tasks.find(function (item) { return item.id === taskId; }); if (task) task.done = true; persistState(); updateCounters(); openDrawer('tasks', '待办中心', 'MY WORK QUEUE', taskDrawerHTML()); return; }
    if (button.hasAttribute('data-notice-read-all')) { state.notifications.forEach(function (item) { item.read = true; }); persistState(); updateCounters(); openDrawer('notifications', '消息中心', 'NOTIFICATIONS', notificationDrawerHTML()); return; }
    if (button.hasAttribute('data-notice-route')) { var notice = button.closest('[data-notice-id]'); var noticeItem = notice ? state.notifications.find(function (item) { return item.id === notice.getAttribute('data-notice-id'); }) : null; if (noticeItem) noticeItem.read = true; persistState(); updateCounters(); closeDrawer(); navigate(button.getAttribute('data-notice-route')); return; }
    if (button.hasAttribute('data-module-route')) { closeDrawer(); navigate(button.getAttribute('data-module-route')); return; }
    if (button.hasAttribute('data-command-id')) { executeCommand(button.getAttribute('data-command-id')); return; }
    if (button.hasAttribute('data-modal-action')) { handleModalAction(button.getAttribute('data-modal-action')); return; }
    if (button.closest('.module-tabs')) {
      var tabs = button.closest('.module-tabs');
      $$('button', tabs).forEach(function (item) { item.classList.toggle('active', item === button); });
      if (button !== $('button', tabs)) openDrawer('module', button.textContent.replace(/\d+/g, '').trim(), (tabs.getAttribute('data-module') || 'MODULE').toUpperCase(), moduleDrawerHTML(tabs.getAttribute('data-module'), button.textContent.replace(/\d+/g, '').trim()));
      return;
    }
    if (button.id === 'startCollection') { openCollectionForm(); return; }
    if (button.id === 'runCollectionBtn') { runCollectionJob(); return; }
    if (button.classList.contains('inspect-corpus')) { openCorpusDetail(button.closest('.corpus-row')); return; }
    if (button.classList.contains('approve-corpus')) { openCorpusReview(button); return; }
    if (button.closest('#corpusTags')) { $$('#corpusTags button').forEach(function (item) { item.classList.toggle('active', item === button); }); state.corpusFilter = button.getAttribute('data-filter') || 'all'; filterCorpus(); return; }
    if (button.hasAttribute('data-zljj-tab')) { switchZheliJianjiaoTab(button.getAttribute('data-zljj-tab')); return; }
    if (button.id === 'generateScriptBtn') { generateScript(); return; }
    if (button.id === 'openReviewModal' || button.classList.contains('small-primary')) { openContentReview(); return; }
    if (button.id === 'runOriginality') { runOriginality(); return; }
    if (button.id === 'composeVideoBtn') { composeVideo(); return; }
    if (button.id === 'uploadResource') { openResourceUpload(); return; }
    if (button.id === 'batchManage') { $('#resourceGrid').classList.toggle('batch-mode'); button.classList.toggle('active'); return; }
    if (button.classList.contains('resource-detail') || button.closest('.resource-cover')) { openResourceDetail(button.closest('.resource-card')); return; }
    if (button.closest('.view-switch')) { $$('.view-switch button').forEach(function (item) { item.classList.toggle('active', item === button); }); $('#resourceGrid').classList.toggle('list-view', button.getAttribute('aria-label') === '列表视图'); return; }
    if (button.id === 'bookExpert' || button.id === 'bookExpertTop') { openAppointment(); return; }
    if (button.id === 'clearExpertFilters') { resetExpertFilters(); return; }
    if (button.hasAttribute('data-expert-page')) { changeExpertPage(button.getAttribute('data-expert-page')); return; }
    if (button.classList.contains('select-expert')) { selectExpertById(button.getAttribute('data-expert-id') || button.closest('.expert-card').getAttribute('data-expert-id')); return; }
    if (button.closest('.date-options') || button.closest('.time-options')) { $$('button', button.parentElement).forEach(function (item) { item.classList.toggle('active', item === button); }); return; }
    if (button.id === 'showCompletedActivity') { openDrawer('activities', '活动台账', 'ACTIVITY LEDGER', activityLedgerHTML()); return; }
    if (button.id === 'resetChat') { newConversation(); return; }
    if (button.closest('.quick-questions')) { if ($('#assistantInput')) $('#assistantInput').value = button.textContent.trim(); sendQuestion(); return; }
    if (button.id === 'sendQuestion') { sendQuestion(); return; }
    if (button.hasAttribute('data-dashboard-tab')) { $$('.literacy-stages button').forEach(function (item) { item.classList.toggle('active', item === button); }); state.analyticsTab = button.getAttribute('data-dashboard-tab') || 'overview'; drawTrendChart(); return; }
    if (button.id === 'uploadSurvey') { openSurveyUpload(); return; }
    if (button.id === 'generateTopic' || button.id === 'screenTopicShortcut') { openTopicTask(); return; }
  }

  function handleInput(event) {
    if (event.target.id === 'corpusSearch') filterCorpus();
    if (event.target.id === 'mediaSearch') filterMedia();
    if (event.target.id === 'expertSearch') filterExperts(true);
    if (event.target.id === 'commandSearchInput') { commandActiveIndex = 0; renderCommandResults(event.target.value); }
    if (event.target.closest('.review-toolbar') && event.target.tagName === 'INPUT') {
      var query = event.target.value.trim().toLowerCase();
      $$('.review-list .table-row:not(.table-header)').forEach(function (row) { row.style.display = !query || row.textContent.toLowerCase().indexOf(query) !== -1 ? '' : 'none'; });
    }
  }

  function handleChange(event) {
    if (event.target.id === 'corpusSource') filterCorpus();
    if (['mediaDisease', 'mediaType', 'mediaAudience'].indexOf(event.target.id) !== -1) filterMedia();
    if (event.target.id === 'expertLibrary' || event.target.id === 'expertRole') filterExperts(true);
    if (event.target.id === 'populationFilter') updateAnalytics();
  }

  function handleKeydown(event) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); openCommandPalette(); return; }
    if (event.key === 'Escape') { closeUserMenu(); if (drawerType) closeDrawer(); else closeModal(); return; }
    if (event.target.id === 'commandSearchInput') {
      var results = $$('#commandResults [data-command-id]');
      if (event.key === 'ArrowDown') { event.preventDefault(); commandActiveIndex = Math.min(results.length - 1, commandActiveIndex + 1); renderCommandResults(event.target.value); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); commandActiveIndex = Math.max(0, commandActiveIndex - 1); renderCommandResults(event.target.value); }
      else if (event.key === 'Enter' && results[commandActiveIndex]) { event.preventDefault(); executeCommand(results[commandActiveIndex].getAttribute('data-command-id')); }
    }
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && event.target.id === 'assistantInput') { event.preventDefault(); sendQuestion(); }
  }

  function handleChartClick(event) {
    if (!state.chartPoints.length) return;
    var rect = event.currentTarget.getBoundingClientRect();
    var x = event.clientX - rect.left;
    var point = state.chartPoints.reduce(function (best, item) { return Math.abs(item.x - x) < Math.abs(best.x - x) ? item : best; }, state.chartPoints[0]);
    toast(point.label + '：全省 ' + point.primary.toFixed(1) + '%，65 岁以上 ' + point.secondary.toFixed(1) + '%。', 'info', '趋势数据');
  }

  function init() {
    loadState();
    buildCommandItems();
    updateTenantUI();
    updateCounters();
    updateTimeLabels();
    window.setInterval(updateTimeLabels, 1000);
    renderCreationState();
    initExpertDirectory();
    updateAnalytics();
    document.addEventListener('click', handleDocumentClick);
    document.addEventListener('input', handleInput);
    document.addEventListener('change', handleChange);
    document.addEventListener('keydown', handleKeydown);
    window.addEventListener('hashchange', function () { renderRoute(parseRoute()); });
    window.addEventListener('resize', function () { window.clearTimeout(resizeTimer); resizeTimer = window.setTimeout(function () { if (state.route === 'analytics') { drawTrendChart(); drawZhejiangMap(); } }, 100); });
    document.addEventListener('fullscreenchange', updateFullscreenButton);
    if ($('#drawerScrim')) $('#drawerScrim').addEventListener('click', closeDrawer);
    if ($('#modalBackdrop')) $('#modalBackdrop').addEventListener('click', function (event) { if (event.target === event.currentTarget) closeModal(); });
    if ($('#trendChart')) $('#trendChart').addEventListener('click', handleChartClick);
    if (!window.location.hash) window.location.hash = '#/overview';
    else renderRoute(parseRoute());
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
