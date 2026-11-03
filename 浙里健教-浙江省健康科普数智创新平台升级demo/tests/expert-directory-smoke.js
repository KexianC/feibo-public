'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');

class MockClassList {
  constructor() { this.values = new Set(); }
  add(...values) { values.forEach((value) => this.values.add(value)); }
  remove(...values) { values.forEach((value) => this.values.delete(value)); }
  contains(value) { return this.values.has(value); }
  toggle(value, force) {
    const enabled = force === undefined ? !this.values.has(value) : Boolean(force);
    if (enabled) this.values.add(value);
    else this.values.delete(value);
    return enabled;
  }
}

class MockElement {
  constructor(id, value = '') {
    this.id = id;
    this.value = value;
    this.innerHTML = '';
    this.textContent = '';
    this.className = '';
    this.classList = new MockClassList();
    this.attributes = Object.create(null);
    this.style = Object.create(null);
    this.disabled = false;
  }
  getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  hasAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name); }
  querySelector() { return null; }
  querySelectorAll() { return []; }
  addEventListener() {}
  closest(selector) { return selector.indexOf('button') !== -1 ? this : null; }
  focus() {}
  scrollIntoView() {}
}

const nodeIds = [
  'expertSearch', 'expertLibrary', 'expertRole', 'expertResultCount', 'expertCards', 'expertPagination',
  'selectedExpertName', 'selectedExpertRole', 'selectedExpertLibraryBadge', 'selectedExpertLibrary',
  'selectedExpertRoleDetail', 'selectedExpertLeadUnit', 'selectedExpertOrdinal',
  'modalBackdrop', 'modalContent', 'modalWindow'
];
const nodes = Object.fromEntries(nodeIds.map((id) => [id, new MockElement(id)]));
nodes.expertLibrary.value = 'all';
nodes.expertRole.value = 'all';

const listeners = Object.create(null);
const documentMock = {
  readyState: 'complete',
  body: new MockElement('body'),
  querySelector(selector) {
    const match = /^#([A-Za-z0-9_-]+)$/.exec(selector);
    return match ? (nodes[match[1]] || null) : null;
  },
  querySelectorAll() { return []; },
  addEventListener(type, callback) { (listeners[type] || (listeners[type] = [])).push(callback); },
  createElement(tag) { return new MockElement(tag); }
};

const locationMock = { hash: '#/experts' };
const localStorageMock = { getItem() { return null; }, setItem() {} };
const windowMock = {
  document: documentMock,
  location: locationMock,
  localStorage: localStorageMock,
  devicePixelRatio: 1,
  setTimeout(callback) { callback(); return 1; },
  clearTimeout() {},
  setInterval() { return 1; },
  requestAnimationFrame(callback) { callback(); return 1; },
  addEventListener() {}
};

const context = vm.createContext({
  window: windowMock,
  document: documentMock,
  location: locationMock,
  console,
  Date,
  Math,
  Number,
  String,
  Array,
  Object,
  JSON,
  Set,
  Promise
});

[
  'assets/expert-data.js',
  'assets/expert-data-1.js',
  'assets/expert-data-2.js',
  'assets/expert-data-3.js',
  'assets/app.js'
].forEach((relativePath) => {
  vm.runInContext(fs.readFileSync(path.join(root, relativePath), 'utf8'), context, { filename: relativePath });
});

const directory = windowMock.ZJ_EXPERT_LIBRARY;
assert.strictEqual(directory.libraries.length, 24, '应加载 24 个科普分库');
assert.strictEqual(directory.libraries.reduce((sum, library) => sum + library.people.length, 0), 2351, '应加载 2,351 名成员');
assert.match(nodes.expertResultCount.textContent, /2,351 名成员/, '初始化应展示完整名录人数');
assert.strictEqual((nodes.expertCards.innerHTML.match(/class="expert-card /g) || []).length, 9, '每页应渲染 9 张成员卡片');
assert.strictEqual(nodes.selectedExpertName.textContent, '\u738b\u5efa\u5b89', '默认应选中王建安');
assert.strictEqual(nodes.selectedExpertLibrary.textContent, '\u6162\u75c5\u5fc3\u8840\u7ba1\u75be\u75c5\u7ba1\u7406\u79d1\u666e\u5206\u5e93');

nodes.expertSearch.value = '\u738b\u5efa\u5b89';
listeners.input[0]({ target: nodes.expertSearch });
assert.match(nodes.expertResultCount.textContent, /1 名成员/, '姓名检索应返回唯一成员');
assert.match(nodes.expertCards.innerHTML, /\u738b\u5efa\u5b89/, '检索结果应包含目标成员');

nodes.expertSearch.value = '';
nodes.expertLibrary.value = 'LIB-02';
listeners.change[0]({ target: nodes.expertLibrary });
assert.match(nodes.expertResultCount.textContent, /90 名成员/, '分库筛选人数应与官方名单一致');

nodes.expertRole.value = '\u7ec4\u957f';
listeners.change[0]({ target: nodes.expertRole });
assert.match(nodes.expertResultCount.textContent, /1 名成员/, '角色与分库交叉筛选应生效');

function clickButton(id, attrs = {}, classes = []) {
  const button = new MockElement(id);
  Object.entries(attrs).forEach(([name, value]) => button.setAttribute(name, value));
  classes.forEach((value) => button.classList.add(value));
  listeners.click[0]({ target: button });
}

clickButton('clearExpertFilters');
assert.match(nodes.expertResultCount.textContent, /2,351 名成员/, '重置筛选应恢复完整名录');

clickButton('pageNext', { 'data-expert-page': 'next' });
assert.match(nodes.expertResultCount.textContent, /第 2 \/ 262 页/, '分页应进入第 2 页');

clickButton('selectMao', { 'data-expert-id': 'LIB-02-P002' }, ['select-expert']);
assert.strictEqual(nodes.selectedExpertName.textContent, '\u6bdb\u5251\u6ce2');
assert.strictEqual(nodes.selectedExpertRoleDetail.textContent, '\u526f\u7ec4\u957f / \u8054\u7edc\u5458', '兼任角色应合并展示');
assert.strictEqual(nodes.selectedExpertLeadUnit.textContent, '\u6d59\u6c5f\u7701\u4eba\u6c11\u533b\u9662');

clickButton('bookExpert');
assert.match(nodes.modalContent.innerHTML, /\u6bdb\u5251\u6ce2/, '预约弹窗应带入选中成员');
assert.match(nodes.modalContent.innerHTML, /\u5206\u5e93\u7275\u5934\u5355\u4f4d/, '预约弹窗应明确标注分库牵头单位');
assert.doesNotMatch(nodes.modalContent.innerHTML, /\u64c5\u957f|\u804c\u79f0|\u4e2a\u4eba\u5355\u4f4d|\u670d\u52a1\u533a\u57df/, '预约流程不得引入官网未公布的个人字段');

console.log(JSON.stringify({
  status: 'ok',
  libraries: 24,
  members: 2351,
  pageSize: 9,
  totalPages: 262,
  filters: ['keyword', 'library', 'role'],
  appointment: 'linked'
}, null, 2));
