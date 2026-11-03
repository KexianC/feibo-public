<template>
  <div class="geo-workbench-page">
    <el-tabs v-model="activeTab" class="geo-tabs">
      <el-tab-pane label="查询库" name="queries">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="queryFilter.departmentId" placeholder="科室" clearable filterable class="filter" @change="loadQueries">
                <el-option v-for="item in departmentOptions" :key="item.id" :label="item.departmentName" :value="item.id" />
              </el-select>
              <el-select v-model="queryFilter.doctorId" placeholder="医生" clearable filterable class="filter" @change="loadQueries">
                <el-option v-for="item in doctorOptions" :key="item.id" :label="`${item.doctorName} - ${item.departmentName || ''}`" :value="item.id" />
              </el-select>
              <el-input v-model="queryFilter.disease" placeholder="疾病/病种" clearable class="filter" @keyup.enter="reloadQueries" />
              <el-select v-model="queryFilter.enabled" placeholder="状态" clearable class="filter-sm" @change="loadQueries">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadQueries">查询</el-button>
            </div>
            <div class="toolbar-right">
              <el-button type="success" plain :icon="Plus" @click="openOfficialSiteMonitor">添加官网监测</el-button>
              <el-button :icon="Plus" @click="openManualQuery">新增问题</el-button>
              <el-button type="primary" :icon="MagicStick" @click="showGenerateDialog = true">生成问题簇</el-button>
            </div>
          </div>

          <el-table :data="queries" stripe v-loading="queryLoading" empty-text="暂无 GEO 查询">
            <el-table-column prop="queryText" label="问题" min-width="280" show-overflow-tooltip />
            <el-table-column prop="queryCluster" label="问题簇" width="120" show-overflow-tooltip />
            <el-table-column prop="disease" label="疾病" width="140" show-overflow-tooltip />
            <el-table-column prop="departmentName" label="科室" width="140" show-overflow-tooltip />
            <el-table-column prop="doctorName" label="医生" width="110" show-overflow-tooltip />
            <el-table-column prop="region" label="地区" width="100" />
            <el-table-column prop="treatmentScenario" label="场景" width="150" show-overflow-tooltip />
            <el-table-column label="监测开关" width="120" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  :active-value="1"
                  :inactive-value="0"
                  :loading="updatingQueryId === row.id"
                  @change="value => updateQueryEnabled(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column label="频率" width="150" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.monitorIntervalHours"
                  :min="1"
                  :max="720"
                  :step="1"
                  size="small"
                  controls-position="right"
                  :disabled="updatingQueryId === row.id"
                  @change="value => updateQueryInterval(row, value)"
                />
                <span class="unit-text">小时</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="isQueryEnabled(row) ? 'success' : 'info'" size="small">{{ isQueryEnabled(row) ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="监测" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :loading="monitoringQueryId === row.id" @click="runMonitorForQuery(row)">立即监测</el-button>
                <el-button link type="primary" size="small" :loading="probingQueryId === row.id" @click="runProbeForQuery(row)">诊断</el-button>
                <el-button link size="small" @click="openGenerateFeedback(row)">补充建议</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="queryPage.pageNum"
              v-model:page-size="queryPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="queryPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadQueries"
              @current-change="loadQueries"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="平台矩阵" name="matrix">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input-number v-model="matrixLimit" :min="10" :max="100" :step="10" controls-position="right" />
              <el-button type="primary" :icon="Search" @click="loadMatrix">刷新</el-button>
            </div>
          </div>
          <el-table :data="matrixRows" stripe v-loading="matrixLoading" empty-text="暂无平台矩阵数据">
            <el-table-column prop="queryText" label="问题" min-width="280" show-overflow-tooltip fixed="left" />
            <el-table-column v-for="engine in matrixEngines" :key="engine" :label="engine" min-width="170" align="center">
              <template #default="{ row }">
                <div v-if="platformCell(row, engine)" class="platform-cell">
                  <el-tag :type="toNumber(platformCell(row, engine).mentioned) === 1 ? 'success' : 'danger'" size="small">
                    {{ toNumber(platformCell(row, engine).mentioned) === 1 ? '提及' : '未提及' }}
                  </el-tag>
                  <el-tag :type="toNumber(platformCell(row, engine).cited) === 1 ? 'success' : 'warning'" size="small">
                    {{ toNumber(platformCell(row, engine).cited) === 1 ? '引用' : '未引用' }}
                  </el-tag>
                  <span>GEO {{ scoreValue(platformCell(row, engine).visibilityScore) }}</span>
                  <span>排名 {{ platformCell(row, engine).rankPosition || '-' }}</span>
                </div>
                <span v-else class="muted">未监测</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="趋势看板" name="trend">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="trendFilter.dimension" class="filter-sm" @change="loadTrend">
                <el-option label="医院" value="hospital" />
                <el-option label="科室" value="department" />
                <el-option label="医生" value="doctor" />
              </el-select>
              <el-input-number v-model="trendFilter.days" :min="1" :max="90" controls-position="right" />
              <el-button type="primary" :icon="Search" @click="loadTrend">刷新</el-button>
            </div>
          </div>
          <el-table :data="trendPoints" stripe v-loading="trendLoading" empty-text="暂无趋势数据">
            <el-table-column prop="date" label="日期" width="130" />
            <el-table-column prop="scopeName" label="维度对象" min-width="180" show-overflow-tooltip />
            <el-table-column label="GEO分" min-width="240">
              <template #default="{ row }">
                <div class="trend-cell">
                  <el-progress :percentage="scoreValue(row.score)" :stroke-width="10" :show-text="false" :color="scoreColor" />
                  <span>{{ scoreValue(row.score) }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="来源资产" name="sources">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="sourceFilter.sourceType" placeholder="来源类型" clearable class="filter" @change="reloadSources">
                <el-option label="医院官网" value="official_site" />
                <el-option label="政府来源" value="government" />
                <el-option label="学术来源" value="academic" />
                <el-option label="官方账号" value="official_social" />
                <el-option label="内容平台" value="publishing_platform" />
                <el-option label="其他第三方" value="third_party" />
              </el-select>
              <el-select v-model="sourceFilter.official" placeholder="是否官方" clearable class="filter-sm" @change="reloadSources">
                <el-option label="官方" :value="1" />
                <el-option label="外部" :value="0" />
              </el-select>
              <el-input v-model="sourceFilter.domain" placeholder="域名" clearable class="filter" @keyup.enter="reloadSources" />
              <el-button type="primary" :icon="Search" @click="loadSources">刷新</el-button>
            </div>
            <div class="toolbar-right">
              <el-button :icon="Refresh" :loading="syncingSources" @click="syncSourceAssets">同步历史引用</el-button>
            </div>
          </div>
          <el-table :data="sourceAssets" stripe v-loading="sourceLoading" empty-text="暂无来源资产">
            <el-table-column prop="title" label="来源" min-width="220" show-overflow-tooltip />
            <el-table-column prop="url" label="URL" min-width="300" show-overflow-tooltip />
            <el-table-column prop="domain" label="域名" width="170" show-overflow-tooltip />
            <el-table-column label="类型" width="110" align="center">
              <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column label="权威分" width="150">
              <template #default="{ row }">
                <div class="trend-cell">
                  <el-progress :percentage="scoreValue(row.authorityScore)" :stroke-width="8" :show-text="false" :color="scoreColor" />
                  <span>{{ scoreValue(row.authorityScore) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="归属" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ entityTypeLabel(row.entityType) }} · {{ row.entityName || '-' }}</template>
            </el-table-column>
            <el-table-column label="官方" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="toNumber(row.official) === 1 ? 'success' : 'info'" size="small">{{ toNumber(row.official) === 1 ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="citationCount" label="引用次数" width="90" align="center" />
            <el-table-column label="平台" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ formatJsonList(row.engines) }}</template>
            </el-table-column>
            <el-table-column prop="usageRecommendation" label="使用建议" min-width="240" show-overflow-tooltip />
            <el-table-column label="最近引用" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.lastSeenTime) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="sourcePage.pageNum"
              v-model:page-size="sourcePage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="sourcePage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadSources"
              @current-change="loadSources"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="探针实验室" name="probes">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="probeFilter.engine" placeholder="平台" clearable filterable class="filter" @change="reloadProbes">
                <el-option v-for="item in monitorEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="probeFilter.status" placeholder="状态" clearable class="filter-sm" @change="reloadProbes">
                <el-option label="排队中" :value="0" />
                <el-option label="运行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="失败" :value="3" />
              </el-select>
              <el-select v-model="probeFilter.probeMode" placeholder="诊断方式" clearable class="filter" @change="reloadProbes">
                <el-option label="平台自审" value="platform_self_review" />
                <el-option label="证据分析" value="evidence_analysis" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadProbes">查询</el-button>
            </div>
            <div class="toolbar-right">
              <el-select v-model="probeRunEngines" multiple collapse-tags collapse-tags-tooltip placeholder="运行平台" class="filter">
                <el-option v-for="item in monitorEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input-number v-model="probeLimit" :min="1" :max="50" controls-position="right" />
              <el-input-number v-model="probeRounds" :min="1" :max="2" controls-position="right" />
              <el-button type="primary" :icon="VideoPlay" :loading="runningProbes" @click="runProbeBatch">运行探针</el-button>
            </div>
          </div>
          <el-table :data="probeList" stripe v-loading="probeLoading" empty-text="暂无探针记录">
            <el-table-column prop="engine" label="平台" width="130" />
            <el-table-column prop="queryText" label="真实搜索问题" min-width="280" show-overflow-tooltip />
            <el-table-column label="诊断方式" width="100" align="center">
              <template #default="{ row }">{{ probeModeLabel(row.probeMode) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag :type="probeStatusTag(row.status)" size="small">{{ probeStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="提及" width="75" align="center"><template #default="{ row }">{{ yesNo(row.mentioned) }}</template></el-table-column>
            <el-table-column label="引用" width="75" align="center"><template #default="{ row }">{{ yesNo(row.cited) }}</template></el-table-column>
            <el-table-column prop="officialSourceCount" label="官方源" width="80" align="center" />
            <el-table-column prop="visibilityScore" label="GEO分" width="80" align="center" />
            <el-table-column prop="gapSummary" label="差距诊断" min-width="260" show-overflow-tooltip />
            <el-table-column label="回合" width="80" align="center">
              <template #default="{ row }">{{ row.completedRounds || 0 }}/{{ row.requestedRounds || 1 }}</template>
            </el-table-column>
            <el-table-column label="时间" width="160" align="center"><template #default="{ row }">{{ formatDate(row.finishedTime || row.createdTime) }}</template></el-table-column>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openProbe(row)">查看</el-button></template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="probePage.pageNum"
              v-model:page-size="probePage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="probePage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadProbes"
              @current-change="loadProbes"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="官网建议" name="siteRecommendations">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="siteFilter.status" placeholder="交接状态" clearable class="filter-sm" @change="reloadSiteRecommendations">
                <el-option label="待提交" :value="0" />
                <el-option label="已提交" :value="1" />
                <el-option label="已采纳" :value="2" />
                <el-option label="未采纳" :value="3" />
                <el-option label="已完成" :value="4" />
              </el-select>
              <el-select v-model="siteFilter.entityType" placeholder="对象" clearable class="filter-sm" @change="reloadSiteRecommendations">
                <el-option label="医院" value="hospital" />
                <el-option label="科室" value="department" />
                <el-option label="医生" value="doctor" />
              </el-select>
              <el-select v-model="siteFilter.recommendationType" placeholder="建议类型" clearable class="filter" @change="reloadSiteRecommendations">
                <el-option label="权威来源" value="source_authority" />
                <el-option label="FAQ/结构化数据" value="faq_schema" />
                <el-option label="本地化内容" value="local_content" />
                <el-option label="内容结构" value="content_structure" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadSiteRecommendations">查询</el-button>
            </div>
            <el-tag type="warning" effect="plain">仅生成建议 · 医院方执行</el-tag>
          </div>
          <el-table :data="siteRecommendationList" stripe v-loading="siteRecommendationLoading" empty-text="暂无官网修改建议">
            <el-table-column prop="entityName" label="对象" width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="120"><template #default="{ row }">{{ recommendationTypeLabel(row.recommendationType) }}</template></el-table-column>
            <el-table-column prop="title" label="建议标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="currentGap" label="当前差距" min-width="240" show-overflow-tooltip />
            <el-table-column prop="recommendedChange" label="建议修改" min-width="280" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column label="合规" width="80" align="center">
              <template #default="{ row }"><el-tag :type="complianceTag(row.complianceStatus)" size="small">{{ complianceLabel(row.complianceStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="执行边界" width="100" align="center"><template #default><el-tag type="warning" size="small">仅建议</el-tag></template></el-table-column>
            <el-table-column label="交接状态" width="130" align="center">
              <template #default="{ row }">
                <el-select v-model="row.status" size="small" :disabled="updatingSiteId === row.id" @change="value => updateSiteStatus(row, value)">
                  <el-option label="待提交" :value="0" />
                  <el-option label="已提交" :value="1" />
                  <el-option label="已采纳" :value="2" />
                  <el-option label="未采纳" :value="3" />
                  <el-option label="已完成" :value="4" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160" align="center"><template #default="{ row }">{{ formatDate(row.createdTime) }}</template></el-table-column>
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }"><el-button link type="primary" size="small" @click="openSiteRecommendation(row)">查看</el-button></template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="sitePage.pageNum"
              v-model:page-size="sitePage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="sitePage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadSiteRecommendations"
              @current-change="loadSiteRecommendations"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="监测记录" name="monitor">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="monitorFilter.engine" placeholder="监测平台" clearable filterable class="filter" @change="loadMonitorRecords">
                <el-option v-for="item in monitorEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="monitorFilter.mentioned" placeholder="是否提及" clearable class="filter-sm" @change="loadMonitorRecords">
                <el-option label="提及" :value="1" />
                <el-option label="未提及" :value="0" />
              </el-select>
              <el-select v-model="monitorFilter.cited" placeholder="是否引用" clearable class="filter-sm" @change="loadMonitorRecords">
                <el-option label="引用" :value="1" />
                <el-option label="未引用" :value="0" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadMonitorRecords">查询</el-button>
            </div>
            <div class="toolbar-right">
              <el-select v-model="monitorRunEngines" multiple collapse-tags collapse-tags-tooltip placeholder="运行平台" clearable class="filter">
                <el-option v-for="item in monitorEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button type="primary" :icon="VideoPlay" :loading="runningBatch" @click="runMonitorBatch">运行批次</el-button>
            </div>
          </div>
          <el-table :data="monitorRecords" stripe v-loading="monitorLoading" empty-text="暂无监测记录">
            <el-table-column prop="engine" label="引擎" width="120" />
            <el-table-column prop="queryText" label="问题" min-width="260" show-overflow-tooltip />
            <el-table-column label="GEO分" width="92" align="center">
              <template #default="{ row }">
                <div class="score-cell">
                  <el-progress :percentage="scoreValue(row.visibilityScore)" :stroke-width="6" :show-text="false" :color="scoreColor" />
                  <span>{{ scoreValue(row.visibilityScore) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="提及" width="80" align="center">
              <template #default="{ row }"><el-tag :type="toNumber(row.mentioned) === 1 ? 'success' : 'danger'" size="small">{{ yesNo(row.mentioned) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="引用" width="80" align="center">
              <template #default="{ row }"><el-tag :type="toNumber(row.cited) === 1 ? 'success' : 'warning'" size="small">{{ yesNo(row.cited) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="排名" width="80" align="center">
              <template #default="{ row }">{{ row.rankPosition || '-' }}</template>
            </el-table-column>
            <el-table-column label="来源" width="92" align="center">
              <template #default="{ row }">{{ row.sourceCount || 0 }} / {{ row.officialSourceCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="center">
              <template #default="{ row }">{{ formatLatency(row.latencyMs) }}</template>
            </el-table-column>
            <el-table-column prop="citedUrls" label="引用 URL" min-width="220" show-overflow-tooltip />
            <el-table-column prop="matchedTerms" label="命中词" width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag :type="toNumber(row.status) === 1 ? 'success' : 'danger'" size="small">{{ toNumber(row.status) === 1 ? '成功' : '失败' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="时间" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.monitoredTime || row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRecord(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="monitorPage.pageNum"
              v-model:page-size="monitorPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="monitorPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadMonitorRecords"
              @current-change="loadMonitorRecords"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="优化任务" name="tasks">
        <div class="panel-card">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="feedbackFilter.queryId" placeholder="查询 ID" clearable class="filter" @keyup.enter="reloadFeedback" />
              <el-select v-model="feedbackFilter.status" placeholder="状态" clearable class="filter-sm" @change="loadFeedback">
                <el-option label="待处理" :value="0" />
                <el-option label="已处理" :value="1" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadFeedback">查询</el-button>
            </div>
            <el-button type="primary" :icon="MagicStick" :loading="generatingFeedback" @click="generateFeedbackBatch">生成建议</el-button>
          </div>
          <el-table :data="feedbackList" stripe v-loading="feedbackLoading" empty-text="暂无反馈建议">
            <el-table-column prop="suggestionType" label="类型" width="110" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="missReason" label="未命中原因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="content" label="补充内容" min-width="280" show-overflow-tooltip />
            <el-table-column prop="structuredSummary" label="结构化摘要" min-width="220" show-overflow-tooltip />
            <el-table-column label="合规" width="90" align="center">
              <template #default="{ row }"><el-tag :type="complianceTag(row.complianceStatus)" size="small">{{ complianceLabel(row.complianceStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="外部发布登记" width="145" align="center">
              <template #default="{ row }"><el-tag :type="publishTag(row.publishStatus)" size="small">{{ publishLabel(row.publishStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="复测" width="90" align="center">
              <template #default="{ row }"><el-tag :type="retestTag(row.retestStatus)" size="small">{{ retestLabel(row.retestStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="渠道" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ formatJsonList(row.targetChannels) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag :type="toNumber(row.status) === 1 ? 'success' : 'warning'" size="small">{{ toNumber(row.status) === 1 ? '已处理' : '待处理' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="时间" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openFeedback(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="openFeedbackHandling(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="feedbackPage.pageNum"
              v-model:page-size="feedbackPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="feedbackPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadFeedback"
              @current-change="loadFeedback"
            />
          </div>

          <el-divider />

          <div class="toolbar">
            <div class="toolbar-left">
              <el-select v-model="taskFilter.status" placeholder="任务状态" clearable class="filter-sm" @change="loadMonitorTasks">
                <el-option label="待执行" :value="0" />
                <el-option label="执行中" :value="1" />
                <el-option label="成功" :value="2" />
                <el-option label="失败" :value="3" />
              </el-select>
              <el-select v-model="taskFilter.engine" placeholder="平台" clearable filterable class="filter" @change="loadMonitorTasks">
                <el-option v-for="item in monitorEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="taskFilter.triggerType" placeholder="触发类型" clearable class="filter-sm" @change="loadMonitorTasks">
                <el-option label="人工" value="manual" />
                <el-option label="定时" value="scheduled" />
                <el-option label="复测" value="retest" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadMonitorTasks">查询</el-button>
            </div>
          </div>
          <el-table :data="taskList" stripe v-loading="taskLoading" empty-text="暂无队列任务">
            <el-table-column prop="engine" label="平台" width="120" />
            <el-table-column prop="queryText" label="问题" min-width="260" show-overflow-tooltip />
            <el-table-column prop="triggerType" label="触发" width="90" />
            <el-table-column prop="priority" label="优先级" width="90" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag :type="taskStatusTag(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="计划时间" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.plannedTime) }}</template>
            </el-table-column>
            <el-table-column label="完成时间" width="160" align="center">
              <template #default="{ row }">{{ formatDate(row.finishedTime) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="taskPage.pageNum"
              v-model:page-size="taskPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="taskPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadMonitorTasks"
              @current-change="loadMonitorTasks"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="合规审核" name="compliance">
        <div class="panel-card compliance-grid">
          <section>
            <el-form label-width="90px">
              <el-form-item label="标题">
                <el-input v-model="complianceForm.title" placeholder="待审核标题" />
              </el-form-item>
              <el-form-item label="正文">
                <el-input v-model="complianceForm.content" type="textarea" :rows="12" placeholder="待审核正文" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="auditingText" @click="auditText">审核文本</el-button>
              </el-form-item>
            </el-form>
          </section>
          <section class="result-card">
            <div class="result-header">
              <span>审核结果</span>
              <el-tag v-if="complianceResult" :type="complianceResult.passed ? 'success' : 'danger'">
                {{ complianceResult.passed ? '通过' : '不通过' }}
              </el-tag>
            </div>
            <el-empty v-if="!complianceResult" description="暂无结果" />
            <template v-else>
              <div class="risk-level">风险等级：{{ complianceResult.level || '-' }}</div>
              <el-table :data="complianceResult.violations || []" size="small" border empty-text="未命中违规规则">
                <el-table-column prop="ruleType" label="规则" width="130" />
                <el-table-column prop="severity" label="级别" width="90" />
                <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
                <el-table-column prop="evidence" label="证据" min-width="160" show-overflow-tooltip />
              </el-table>
            </template>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="showWebsiteManagerDialog"
      title="官网 GEO 监测"
      width="900px"
      top="4vh"
      center
      @closed="stopWebsitePolling"
    >
      <section class="website-create-panel">
        <div class="website-section-heading">
          <div>
            <strong>添加官网监测</strong>
            <span>直接检查官网可访问性、索引条件、结构化数据和内容可引用性</span>
          </div>
        </div>
        <el-form :model="websiteCreateForm" class="website-create-form" label-position="top">
          <el-form-item label="关联医院" required>
            <el-select
              v-model="websiteCreateForm.hospitalId"
              placeholder="请选择医院"
              filterable
              style="width: 100%;"
              @change="syncWebsiteHospital"
            >
              <el-option v-for="item in hospitalOptions" :key="item.id" :label="item.hospitalName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="官网名称">
            <el-input v-model="websiteCreateForm.siteName" placeholder="例如：杭州市中医院官网" maxlength="100" />
          </el-form-item>
          <el-form-item label="官网 URL" required class="website-url-field">
            <el-input v-model="websiteCreateForm.siteUrl" placeholder="https://www.example.com" @keyup.enter="submitWebsiteMonitor" />
          </el-form-item>
          <el-form-item label="检测频率">
            <el-input-number
              v-model="websiteCreateForm.monitorIntervalHours"
              :min="1"
              :max="720"
              controls-position="right"
              style="width: 100%;"
            />
          </el-form-item>
          <div class="website-create-actions">
            <el-checkbox v-model="websiteCreateForm.runImmediately">保存后立即检测</el-checkbox>
            <el-button type="primary" :loading="savingWebsite" @click="submitWebsiteMonitor">添加监测</el-button>
          </div>
        </el-form>
      </section>

      <el-divider />

      <section class="website-list-panel">
        <div class="website-section-heading website-list-heading">
          <div>
            <strong>官网监测列表</strong>
            <span v-if="hasWebsiteChecksInProgress">检测任务运行中，结果将自动刷新</span>
          </div>
          <el-button :icon="Refresh" :loading="websiteListLoading" @click="loadWebsiteMonitors">刷新</el-button>
        </div>
        <el-table
          :data="websiteMonitors"
          row-key="id"
          stripe
          max-height="390"
          v-loading="websiteListLoading"
          empty-text="暂无官网监测"
        >
          <el-table-column label="官网" min-width="165">
            <template #default="{ row }">
              <div class="website-name-cell">
                <strong>{{ row.siteName || row.hospitalName || row.domain || '-' }}</strong>
                <a
                  v-if="safeWebsiteUrl(row.normalizedUrl || row.siteUrl)"
                  :href="safeWebsiteUrl(row.normalizedUrl || row.siteUrl)"
                  target="_blank"
                  rel="noopener noreferrer"
                >{{ row.domain || row.normalizedUrl || row.siteUrl }}</a>
                <span v-else>{{ row.domain || row.siteUrl || '-' }}</span>
                <small>{{ row.hospitalName || '-' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="86" align="center">
            <template #default="{ row }">
              <el-tag :type="websiteStatusTag(row.lastCheckStatus)" size="small">
                {{ websiteStatusLabel(row.lastCheckStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="GEO 分" width="92" align="center">
            <template #default="{ row }">
              <span v-if="row.lastScore === null || row.lastScore === undefined" class="muted">-</span>
              <div v-else class="website-score-cell">
                <el-progress :percentage="scoreValue(row.lastScore)" :stroke-width="6" :show-text="false" :color="scoreColor" />
                <span>{{ scoreValue(row.lastScore) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="HTTP" width="67" align="center">
            <template #default="{ row }">{{ row.lastHttpStatus || '-' }}</template>
          </el-table-column>
          <el-table-column label="最近检测" width="130" align="center">
            <template #default="{ row }">{{ formatDate(row.lastCheckedTime) }}</template>
          </el-table-column>
          <el-table-column label="频率" width="80" align="center">
            <template #default="{ row }">{{ row.monitorIntervalHours || 24 }} 小时</template>
          </el-table-column>
          <el-table-column label="定时" width="72" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="toNumber(row.monitorEnabled)"
                :active-value="1"
                :inactive-value="0"
                :loading="updatingWebsiteId === row.id"
                @change="value => updateWebsiteSchedule(row, value)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="142" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :loading="isWebsiteCheckInProgress(row) || runningWebsiteId === row.id"
                :disabled="isWebsiteCheckInProgress(row)"
                @click="runWebsiteCheck(row)"
              >立即检测</el-button>
              <el-button link type="primary" size="small" @click="openWebsiteReport(row)">查看报告</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap website-pagination">
          <el-pagination
            v-model:current-page="websitePage.pageNum"
            v-model:page-size="websitePage.pageSize"
            :page-sizes="[5, 10, 20]"
            :total="websitePage.total"
            layout="total, sizes, prev, pager, next"
            small
            background
            @size-change="reloadWebsiteMonitors"
            @current-change="loadWebsiteMonitors"
          />
        </div>
      </section>
    </el-dialog>

    <el-dialog v-model="showWebsiteReportDialog" :title="websiteReportTitle" width="900px" top="4vh" center>
      <el-skeleton v-if="websiteReportLoading" :rows="8" animated />
      <el-empty v-else-if="!websiteReport?.latestCheck" description="暂无检测报告，请先运行官网检测" />
      <div v-else class="website-report">
        <section class="website-report-overview">
          <el-progress
            type="dashboard"
            :percentage="websiteReportScore"
            :width="132"
            :stroke-width="12"
            :color="scoreColor"
          />
          <div class="website-report-meta">
            <h3>{{ websiteReport.monitor?.siteName || websiteReport.monitor?.hospitalName || '官网检测报告' }}</h3>
            <a
              v-if="safeWebsiteUrl(websiteReport.monitor?.normalizedUrl || websiteReport.monitor?.siteUrl)"
              :href="safeWebsiteUrl(websiteReport.monitor?.normalizedUrl || websiteReport.monitor?.siteUrl)"
              target="_blank"
              rel="noopener noreferrer"
            >{{ websiteReport.monitor?.domain || websiteReport.monitor?.normalizedUrl || websiteReport.monitor?.siteUrl }}</a>
            <span>页面标题：{{ websiteReport.latestCheck?.pageTitle || '-' }}</span>
            <span>HTTP：{{ websiteReport.latestCheck?.httpStatus || websiteReport.monitor?.lastHttpStatus || '-' }}</span>
            <span>检测时间：{{ formatDate(websiteReport.latestCheck?.checkedTime || websiteReport.monitor?.lastCheckedTime) }}</span>
            <span v-if="websiteReport.latestCheck?.errorMessage || websiteReport.monitor?.lastError" class="website-report-error">
              {{ websiteReport.latestCheck?.errorMessage || websiteReport.monitor?.lastError }}
            </span>
          </div>
        </section>

        <section class="website-citation-section">
          <div class="website-report-heading">AI 引用概览</div>
          <div class="website-citation-grid">
            <div><strong>{{ websiteReport.monitor?.citationCount || 0 }}</strong><span>引用次数</span></div>
            <div><strong>{{ websiteReport.monitor?.citingQueryCount || 0 }}</strong><span>覆盖问题</span></div>
            <div><strong>{{ websiteEngineNames(websiteReport.monitor?.citingEngines) }}</strong><span>引用平台</span></div>
            <div><strong>{{ formatDate(websiteReport.monitor?.lastCitationTime) }}</strong><span>最近引用</span></div>
          </div>
        </section>

        <section>
          <div class="website-report-heading">确定性检查项</div>
          <el-table :data="websiteReport.checks || []" border size="small" max-height="360" empty-text="暂无检查项">
            <el-table-column prop="label" label="检查项" min-width="155" />
            <el-table-column label="结果" width="82" align="center">
              <template #default="{ row }">
                <el-tag :type="row.passed ? 'success' : 'danger'" size="small">{{ row.passed ? '通过' : '待优化' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="得分" width="78" align="center">
              <template #default="{ row }">{{ row.passed ? row.weight : 0 }} / {{ row.weight }}</template>
            </el-table-column>
            <el-table-column prop="detail" label="检测依据" min-width="330" show-overflow-tooltip />
          </el-table>
        </section>

        <section class="website-recommendations">
          <div class="website-report-heading">优化建议</div>
          <el-empty v-if="!(websiteReport.recommendations || []).length" description="本次检查未发现待优化项" :image-size="60" />
          <ol v-else>
            <li v-for="(item, index) in websiteReport.recommendations" :key="`${index}-${item}`">{{ item }}</li>
          </ol>
        </section>
      </div>
    </el-dialog>

    <el-dialog v-model="showGenerateDialog" title="生成 GEO 问题簇" width="720px" center>
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="医院">
          <el-select v-model="generateForm.hospitalId" placeholder="医院" clearable filterable style="width: 100%;">
            <el-option v-for="item in hospitalOptions" :key="item.id" :label="item.hospitalName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="generateForm.departmentId" placeholder="科室" clearable filterable style="width: 100%;">
            <el-option v-for="item in departmentOptions" :key="item.id" :label="item.departmentName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="generateForm.doctorId" placeholder="医生" clearable filterable style="width: 100%;">
            <el-option v-for="item in doctorOptions" :key="item.id" :label="`${item.doctorName} - ${item.departmentName || ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="generateForm.region" placeholder="例如：杭州" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="generateKeywords"
            type="textarea"
            :rows="4"
            placeholder="一行一个或用逗号分隔，例如：中医美容、祛斑、痘痘、脱发、针灸减肥"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="generateForm.priority" :min="0" :max="100" />
          <el-checkbox v-model="generateForm.persist" class="persist-check">保存到查询库</el-checkbox>
        </el-form-item>
        <el-form-item label="监测频率">
          <el-input-number v-model="generateForm.monitorIntervalHours" :min="1" :max="720" controls-position="right" />
          <span class="unit-text">小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generatingQueries" @click="submitGenerateQueries">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showManualDialog" title="新增 GEO 问题" width="700px" center>
      <el-form :model="manualForm" label-width="100px">
        <el-form-item label="问题" required>
          <el-input v-model="manualForm.queryText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="问题簇">
          <el-input v-model="manualForm.queryCluster" />
        </el-form-item>
        <el-form-item label="疾病">
          <el-input v-model="manualForm.disease" />
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="manualForm.departmentId" clearable filterable style="width: 100%;" @change="syncManualDepartment">
            <el-option v-for="item in departmentOptions" :key="item.id" :label="item.departmentName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="manualForm.doctorId" clearable filterable style="width: 100%;" @change="syncManualDoctor">
            <el-option v-for="item in doctorOptions" :key="item.id" :label="`${item.doctorName} - ${item.departmentName || ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="manualForm.region" />
        </el-form-item>
        <el-form-item label="场景">
          <el-input v-model="manualForm.treatmentScenario" />
        </el-form-item>
        <el-form-item label="监测频率">
          <el-input-number v-model="manualForm.monitorIntervalHours" :min="1" :max="720" controls-position="right" />
          <span class="unit-text">小时</span>
        </el-form-item>
        <el-form-item label="期望词">
          <el-input v-model="manualForm.expectedTerms" placeholder="用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showManualDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingManual" @click="submitManualQuery">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showFeedbackHandlingDialog" title="处理优化建议" width="720px"
      :close-on-click-modal="false" :close-on-press-escape="!feedbackActionLoading" :show-close="!feedbackActionLoading">
      <template v-if="handlingFeedback">
        <p>{{ handlingFeedback.title }}</p>
        <el-form label-width="110px">
          <el-form-item label="处理状态">
            <el-select v-model="feedbackHandlingForm.status">
              <el-option label="待处理" :value="0" /><el-option label="已处理" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理说明">
            <el-input v-model="feedbackHandlingForm.note" type="textarea" :rows="3" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item><el-button type="primary" :loading="feedbackActionLoading" @click="saveFeedbackHandling">保存处理记录</el-button></el-form-item>
          <el-divider />
          <el-alert title="外部发布由运营人员完成。登记只保存发布链接与确认人，不会自动发布内容或修改官网。" type="info" :closable="false" show-icon />
          <el-form-item label="已发布链接" style="margin-top: 16px">
            <el-input v-model="feedbackPublishedUrl" placeholder="https://…" maxlength="2000" />
          </el-form-item>
          <el-form-item>
            <el-button :loading="feedbackActionLoading" :disabled="toNumber(handlingFeedback.complianceStatus) !== 1" @click="confirmFeedbackPublication">登记已在外部发布</el-button>
            <span v-if="handlingFeedback.publicationConfirmedTime" style="margin-left: 12px">上次登记：{{ formatDate(handlingFeedback.publicationConfirmedTime) }}</span>
          </el-form-item>
          <el-divider />
          <el-form-item label="复测状态">
            <el-tag :type="retestTag(handlingFeedback.retestStatus)">{{ retestLabel(handlingFeedback.retestStatus) }}</el-tag>
            <span style="margin-left: 12px">最近结束：{{ formatDate(handlingFeedback.lastRetestTime) }}</span>
          </el-form-item>
          <el-form-item>
            <el-button :loading="feedbackActionLoading" :disabled="toNumber(handlingFeedback.retestStatus) === 2" @click="retestFeedback">立即执行下一次复测</el-button>
            <el-button :disabled="feedbackActionLoading" @click="refreshHandlingFeedback">刷新状态</el-button>
          </el-form-item>
        </el-form>
      </template>
      <template #footer><el-button :disabled="feedbackActionLoading" @click="showFeedbackHandlingDialog = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" :title="detailTitle" width="820px" center>
      <pre class="json-view">{{ detailText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import { getHospitalAll } from '@/api/HospitalManagementApi'
import { getDepartmentPage } from '@/api/DepartmentManagementApi'
import { getDoctorPageOther } from '@/api/DoctorManagementApi'
import {
  addGeoQuery,
  auditTextCompliance,
  createGeoWebsiteMonitor,
  generateGeoFeedback,
  generateGeoQueries,
  getGeoFeedbackPage,
  getGeoMonitorTaskPage,
  getGeoMonitorRecordPage,
  getGeoPlatformMatrix,
  getGeoQueryPage,
  getGeoProbePage,
  getGeoSiteRecommendationPage,
  getGeoSourceAssetPage,
  getGeoTrend,
  getGeoWebsiteMonitorPage,
  getGeoWebsiteReport,
  rebuildGeoSourceAssets,
  runGeoMonitor,
  runGeoProbes,
  runGeoWebsiteCheck,
  updateGeoQuery,
  updateGeoSiteRecommendationStatus,
  updateGeoWebsiteMonitor,
  getGeoFeedbackDetail,
  updateGeoFeedbackHandling,
  confirmGeoFeedbackPublication,
  requestGeoFeedbackRetest
} from '@/api/GeoApi'

const activeTab = ref('queries')

const hospitalOptions = ref([])
const departmentOptions = ref([])
const doctorOptions = ref([])

const queryFilter = ref({ doctorId: null, departmentId: null, disease: '', enabled: null })
const queryPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const queries = ref([])
const queryLoading = ref(false)
const updatingQueryId = ref(null)

const monitorEngineOptions = [
  { label: 'Qwen 联网搜索', value: 'qwen' },
  { label: '百度千帆 AI Search', value: 'baidu-qianfan' },
  { label: '腾讯 WSA', value: 'tencent-wsa' },
  { label: '智谱 Web Search', value: 'zhipu-web-search' },
  { label: 'Kimi', value: 'kimi' },
  { label: '火山豆包', value: 'volc-doubao' },
  { label: '秘塔 Metaso', value: 'metaso' },
  { label: '博查 Bocha', value: 'bocha' }
]

const matrixLimit = ref(30)
const matrixRows = ref([])
const matrixEngines = ref([])
const matrixLoading = ref(false)

const trendFilter = ref({ dimension: 'hospital', days: 14 })
const trendPoints = ref([])
const trendLoading = ref(false)

const sourceFilter = ref({ sourceType: '', official: null, entityType: '', domain: '' })
const sourcePage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const sourceAssets = ref([])
const sourceLoading = ref(false)
const syncingSources = ref(false)

const probeFilter = ref({ status: null, engine: '', probeMode: '', gapType: '' })
const probeRunEngines = ref(['qwen', 'baidu-qianfan'])
const probeLimit = ref(10)
const probeRounds = ref(1)
const probePage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const probeList = ref([])
const probeLoading = ref(false)
const runningProbes = ref(false)

const siteFilter = ref({ status: null, entityType: '', recommendationType: '' })
const sitePage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const siteRecommendationList = ref([])
const siteRecommendationLoading = ref(false)
const updatingSiteId = ref(null)

const monitorFilter = ref({ engine: '', mentioned: null, cited: null })
const monitorRunEngines = ref(['baidu-qianfan'])
const monitorPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const monitorRecords = ref([])
const monitorLoading = ref(false)
const runningBatch = ref(false)
const monitoringQueryId = ref(null)
const probingQueryId = ref(null)

const feedbackFilter = ref({ queryId: '', status: null })
const feedbackPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const feedbackList = ref([])
const feedbackLoading = ref(false)
const generatingFeedback = ref(false)
const showFeedbackHandlingDialog = ref(false)
const handlingFeedback = ref(null)
const feedbackHandlingForm = ref({ status: 0, note: '' })
const feedbackPublishedUrl = ref('')
const feedbackActionLoading = ref(false)

const taskFilter = ref({ status: null, engine: '', triggerType: '' })
const taskPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const taskList = ref([])
const taskLoading = ref(false)

const showGenerateDialog = ref(false)
const generatingQueries = ref(false)
const generateKeywords = ref('')
const generateForm = ref({ hospitalId: null, departmentId: null, doctorId: null, region: '杭州', priority: 50, monitorIntervalHours: 24, persist: true })

const newWebsiteCreateForm = () => ({
  hospitalId: null,
  siteName: '',
  siteUrl: '',
  monitorIntervalHours: 24,
  runImmediately: true
})
const showWebsiteManagerDialog = ref(false)
const websiteCreateForm = ref(newWebsiteCreateForm())
const savingWebsite = ref(false)
const websiteMonitors = ref([])
const websitePage = ref({ pageNum: 1, pageSize: 5, total: 0 })
const websiteListLoading = ref(false)
const updatingWebsiteId = ref(null)
const runningWebsiteId = ref(null)
let websitePollTimer = null

const showWebsiteReportDialog = ref(false)
const websiteReportLoading = ref(false)
const websiteReport = ref(null)
const websiteReportMonitor = ref(null)

const showManualDialog = ref(false)
const savingManual = ref(false)
const manualForm = ref({})

const showDetailDialog = ref(false)
const detailTitle = ref('')
const detailText = ref('')

const complianceForm = ref({ title: '', content: '' })
const complianceResult = ref(null)
const auditingText = ref(false)

const splitLines = (value) => String(value || '').split(/[\n,，]/).map(item => item.trim()).filter(Boolean)
const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}
const normalizeQueryRow = (row) => ({
  ...row,
  enabled: toNumber(row.enabled, 0),
  priority: toNumber(row.priority, 50),
  monitorIntervalHours: toNumber(row.monitorIntervalHours, 24) || 24
})
const isQueryEnabled = (row) => toNumber(row?.enabled, 0) === 1
const yesNo = (value) => toNumber(value, 0) === 1 ? '是' : '否'
const formatDate = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const scoreValue = (value) => Math.max(0, Math.min(100, Number(value) || 0))
const scoreColor = (value) => {
  if (value >= 80) return '#22c55e'
  if (value >= 50) return '#f59e0b'
  return '#ef4444'
}
const safeWebsiteUrl = (value) => {
  const url = String(value || '').trim()
  return /^https?:\/\//i.test(url) ? url : ''
}
const normalizeWebsiteUrl = (value) => {
  try {
    const url = new URL(String(value || '').trim())
    return ['http:', 'https:'].includes(url.protocol) ? url.toString() : ''
  } catch {
    return ''
  }
}
const websiteStatusLabel = (value) => {
  if (value === null || value === undefined || value === '') return '未检测'
  return ({ 0: '排队中', 1: '检测中', 2: '已完成', 3: '失败' }[Number(value)] || '未知')
}
const websiteStatusTag = (value) => {
  if (value === null || value === undefined || value === '') return 'info'
  return ({ 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' }[Number(value)] || 'info')
}
const normalizeWebsiteMonitorRow = (row) => ({
  ...row,
  lastCheckStatus: row?.latestCheckId ? row.lastCheckStatus : null,
  monitorEnabled: toNumber(row?.monitorEnabled),
  monitorIntervalHours: toNumber(row?.monitorIntervalHours, 24) || 24
})
const isWebsiteCheckInProgress = (row) => {
  const status = row?.lastCheckStatus
  return Boolean(row?.latestCheckId)
    && status !== null
    && status !== undefined
    && [0, 1].includes(Number(status))
}
const hasWebsiteChecksInProgress = computed(() => websiteMonitors.value.some(isWebsiteCheckInProgress))
const websiteReportScore = computed(() => scoreValue(
  websiteReport.value?.latestCheck?.score ?? websiteReport.value?.monitor?.lastScore
))
const websiteReportTitle = computed(() => {
  const monitor = websiteReport.value?.monitor || websiteReportMonitor.value
  return `${monitor?.siteName || monitor?.hospitalName || '官网'} GEO 检测报告`
})
const websiteEngineNames = (values) => {
  const engines = Array.isArray(values) ? values : []
  if (!engines.length) return '暂无'
  const labels = new Map(monitorEngineOptions.map(item => [item.value, item.label]))
  return engines.map(item => labels.get(item) || item).join('、')
}
const formatLatency = (value) => value ? `${value}ms` : '-'
const parseJsonList = (value) => {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
const formatJsonList = (value) => {
  const list = parseJsonList(value)
  return list.length ? list.join('、') : '-'
}
const platformCell = (row, engine) => row?.platforms?.[engine] || null
const complianceLabel = (value) => ({ 0: '待审', 1: '通过', 2: '拦截' }[Number(value)] || '待审')
const complianceTag = (value) => ({ 1: 'success', 2: 'danger' }[Number(value)] || 'warning')
const publishLabel = (value) => ({ 0: '未登记', 1: '历史排队', 2: '人工确认已发布', 3: '失败' }[Number(value)] || '未登记')
const publishTag = (value) => ({ 1: 'warning', 2: 'success', 3: 'danger' }[Number(value)] || 'info')
const retestLabel = (value) => ({ 0: '待复测', 1: '已排期', 2: '复测中', 3: '已完成', 4: '复测失败' }[Number(value)] || '待复测')
const retestTag = (value) => ({ 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[Number(value)] || 'info')
const taskStatusLabel = (value) => ({ 0: '待执行', 1: '执行中', 2: '成功', 3: '失败' }[Number(value)] || '-')
const taskStatusTag = (value) => ({ 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' }[Number(value)] || 'info')
const sourceTypeLabel = (value) => ({
  official_site: '医院官网',
  government: '政府来源',
  academic: '学术来源',
  official_social: '官方账号',
  publishing_platform: '内容平台',
  third_party: '其他第三方'
}[value] || value || '-')
const entityTypeLabel = (value) => ({ hospital: '医院', department: '科室', doctor: '医生', topic: '主题' }[value] || '-')
const probeModeLabel = (value) => ({ platform_self_review: '平台自审', evidence_analysis: '证据分析' }[value] || '-')
const probeStatusLabel = (value) => ({ 0: '排队中', 1: '运行中', 2: '已完成', 3: '失败' }[Number(value)] || '-')
const probeStatusTag = (value) => ({ 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' }[Number(value)] || 'info')
const recommendationTypeLabel = (value) => ({
  source_authority: '权威来源',
  faq_schema: 'FAQ/结构化',
  local_content: '本地化内容',
  content_structure: '内容结构'
}[value] || value || '-')

const detailPayload = computed(() => ({
  hospitalOptions: hospitalOptions.value.length,
  departmentOptions: departmentOptions.value.length,
  doctorOptions: doctorOptions.value.length
}))

const loadOptions = async () => {
  const [hospitalRes, departmentRes, doctorRes] = await Promise.all([
    getHospitalAll(),
    getDepartmentPage({ pageNum: 1, pageSize: 100 }),
    getDoctorPageOther({ pageNum: 1, pageSize: 1000 })
  ])
  hospitalOptions.value = hospitalRes.success ? (hospitalRes.data || []) : []
  departmentOptions.value = departmentRes.success ? (departmentRes.data || []) : []
  doctorOptions.value = doctorRes.success ? (doctorRes.data || []) : []
}

const loadQueries = async () => {
  queryLoading.value = true
  try {
    const res = await getGeoQueryPage({
      pageNum: queryPage.value.pageNum,
      pageSize: queryPage.value.pageSize,
      doctorId: queryFilter.value.doctorId || undefined,
      departmentId: queryFilter.value.departmentId || undefined,
      disease: queryFilter.value.disease || undefined,
      enabled: queryFilter.value.enabled === null ? undefined : queryFilter.value.enabled
    })
    if (res.success) {
      queries.value = (res.data || []).map(normalizeQueryRow)
      queryPage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '查询库加载失败')
    }
  } catch {
    ElMessage.error('查询库加载失败')
  } finally {
    queryLoading.value = false
  }
}

const reloadQueries = () => {
  queryPage.value.pageNum = 1
  loadQueries()
}

const updateQueryEnabled = async (row, value) => {
  const nextValue = toNumber(value, 0) === 1 ? 1 : 0
  const previousValue = nextValue === 1 ? 0 : 1
  updatingQueryId.value = row.id
  try {
    const res = await updateGeoQuery(row.id, { enabled: nextValue })
    if (res.success) {
      Object.assign(row, normalizeQueryRow(res.data || row))
      ElMessage.success(nextValue === 1 ? '已开启监测' : '已关闭监测')
      await Promise.all([loadMonitorTasks(), loadMatrix(), loadTrend()])
    } else {
      row.enabled = previousValue
      ElMessage.error(res.message || '监测开关更新失败')
    }
  } catch {
    row.enabled = previousValue
    ElMessage.error('监测开关更新失败')
  } finally {
    updatingQueryId.value = null
  }
}

const updateQueryInterval = async (row, value) => {
  const interval = Math.max(1, Math.min(720, toNumber(value, 24)))
  row.monitorIntervalHours = interval
  updatingQueryId.value = row.id
  try {
    const res = await updateGeoQuery(row.id, { monitorIntervalHours: interval })
    if (res.success) {
      Object.assign(row, normalizeQueryRow(res.data || row))
      ElMessage.success('监测频率已更新')
      await Promise.all([loadMonitorTasks(), loadMatrix(), loadTrend()])
    } else {
      ElMessage.error(res.message || '监测频率更新失败')
      await loadQueries()
    }
  } catch {
    ElMessage.error('监测频率更新失败')
    await loadQueries()
  } finally {
    updatingQueryId.value = null
  }
}

const loadMatrix = async () => {
  matrixLoading.value = true
  try {
    const res = await getGeoPlatformMatrix({ limit: matrixLimit.value })
    if (res.success) {
      matrixRows.value = res.data?.rows || []
      matrixEngines.value = res.data?.engines || monitorEngineOptions.map(item => item.value)
    } else {
      ElMessage.error(res.message || '平台矩阵加载失败')
    }
  } catch {
    ElMessage.error('平台矩阵加载失败')
  } finally {
    matrixLoading.value = false
  }
}

const loadTrend = async () => {
  trendLoading.value = true
  try {
    const res = await getGeoTrend(trendFilter.value)
    if (res.success) {
      trendPoints.value = res.data?.points || []
    } else {
      ElMessage.error(res.message || '趋势数据加载失败')
    }
  } catch {
    ElMessage.error('趋势数据加载失败')
  } finally {
    trendLoading.value = false
  }
}

const loadSources = async () => {
  sourceLoading.value = true
  try {
    const res = await getGeoSourceAssetPage({
      pageNum: sourcePage.value.pageNum,
      pageSize: sourcePage.value.pageSize,
      sourceType: sourceFilter.value.sourceType || undefined,
      official: sourceFilter.value.official === null ? undefined : sourceFilter.value.official,
      entityType: sourceFilter.value.entityType || undefined,
      domain: sourceFilter.value.domain || undefined
    })
    if (res.success) {
      sourceAssets.value = res.data || []
      sourcePage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '来源资产加载失败')
    }
  } catch {
    ElMessage.error('来源资产加载失败')
  } finally {
    sourceLoading.value = false
  }
}

const reloadSources = () => {
  sourcePage.value.pageNum = 1
  loadSources()
}

const syncSourceAssets = async () => {
  syncingSources.value = true
  try {
    const res = await rebuildGeoSourceAssets(1000)
    if (res.success) {
      ElMessage.success(`已同步 ${res.data || 0} 条历史监测记录`)
      await loadSources()
    } else {
      ElMessage.error(res.message || '历史引用同步失败')
    }
  } catch {
    ElMessage.error('历史引用同步失败')
  } finally {
    syncingSources.value = false
  }
}

const loadProbes = async () => {
  probeLoading.value = true
  try {
    const res = await getGeoProbePage({
      pageNum: probePage.value.pageNum,
      pageSize: probePage.value.pageSize,
      status: probeFilter.value.status === null ? undefined : probeFilter.value.status,
      engine: probeFilter.value.engine || undefined,
      probeMode: probeFilter.value.probeMode || undefined,
      gapType: probeFilter.value.gapType || undefined
    })
    if (res.success) {
      probeList.value = res.data || []
      probePage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '探针记录加载失败')
    }
  } catch {
    ElMessage.error('探针记录加载失败')
  } finally {
    probeLoading.value = false
  }
}

const reloadProbes = () => {
  probePage.value.pageNum = 1
  loadProbes()
}

const runProbeBatch = async () => {
  runningProbes.value = true
  try {
    const res = await runGeoProbes({
      limit: probeLimit.value,
      rounds: probeRounds.value,
      engines: probeRunEngines.value.length ? probeRunEngines.value : undefined
    })
    if (res.success) {
      ElMessage.success(`已排队 ${res.data?.length || 0} 个诊断探针`)
      activeTab.value = 'probes'
      await loadProbes()
    } else {
      ElMessage.error(res.message || '探针排队失败')
    }
  } catch {
    ElMessage.error('探针排队失败，请检查平台配置')
  } finally {
    runningProbes.value = false
  }
}

const runProbeForQuery = async (row) => {
  probingQueryId.value = row.id
  try {
    const res = await runGeoProbes({
      queryIds: [row.id],
      rounds: probeRounds.value,
      engines: probeRunEngines.value.length ? probeRunEngines.value : undefined
    })
    if (res.success) {
      ElMessage.success(`已排队 ${res.data?.length || 0} 个诊断探针`)
      activeTab.value = 'probes'
      await loadProbes()
    } else {
      ElMessage.error(res.message || '探针排队失败')
    }
  } catch {
    ElMessage.error('探针排队失败，请检查平台配置')
  } finally {
    probingQueryId.value = null
  }
}

const openProbe = (row) => {
  detailTitle.value = 'GEO 探针详情'
  detailText.value = JSON.stringify(row, null, 2)
  showDetailDialog.value = true
}

const loadSiteRecommendations = async () => {
  siteRecommendationLoading.value = true
  try {
    const res = await getGeoSiteRecommendationPage({
      pageNum: sitePage.value.pageNum,
      pageSize: sitePage.value.pageSize,
      status: siteFilter.value.status === null ? undefined : siteFilter.value.status,
      entityType: siteFilter.value.entityType || undefined,
      recommendationType: siteFilter.value.recommendationType || undefined
    })
    if (res.success) {
      siteRecommendationList.value = (res.data || []).map(item => ({ ...item, status: toNumber(item.status, 0) }))
      sitePage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '官网建议加载失败')
    }
  } catch {
    ElMessage.error('官网建议加载失败')
  } finally {
    siteRecommendationLoading.value = false
  }
}

const reloadSiteRecommendations = () => {
  sitePage.value.pageNum = 1
  loadSiteRecommendations()
}

const updateSiteStatus = async (row, value) => {
  updatingSiteId.value = row.id
  try {
    const res = await updateGeoSiteRecommendationStatus(row.id, { status: toNumber(value, 0) })
    if (res.success) {
      Object.assign(row, res.data || {})
      row.status = toNumber(row.status, 0)
      ElMessage.success('交接状态已更新')
    } else {
      ElMessage.error(res.message || '交接状态更新失败')
      await loadSiteRecommendations()
    }
  } catch {
    ElMessage.error('交接状态更新失败')
    await loadSiteRecommendations()
  } finally {
    updatingSiteId.value = null
  }
}

const openSiteRecommendation = (row) => {
  detailTitle.value = '官网修改建议'
  detailText.value = JSON.stringify(row, null, 2)
  showDetailDialog.value = true
}

const submitGenerateQueries = async () => {
  generatingQueries.value = true
  try {
    const res = await generateGeoQueries({
      ...generateForm.value,
      keywords: splitLines(generateKeywords.value)
    })
    if (res.success) {
      ElMessage.success(`已生成 ${res.data?.length || 0} 个问题`)
      showGenerateDialog.value = false
      await loadQueries()
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch {
    ElMessage.error('生成失败')
  } finally {
    generatingQueries.value = false
  }
}

const syncWebsiteHospital = (id) => {
  const hospital = hospitalOptions.value.find(item => String(item.id) === String(id))
  if (!hospital) return
  if (!String(websiteCreateForm.value.siteName || '').trim()) {
    websiteCreateForm.value.siteName = `${hospital.hospitalName}官网`
  }
}

const stopWebsitePolling = () => {
  if (websitePollTimer !== null) {
    window.clearTimeout(websitePollTimer)
    websitePollTimer = null
  }
}

const scheduleWebsitePolling = () => {
  stopWebsitePolling()
  if (!showWebsiteManagerDialog.value || !hasWebsiteChecksInProgress.value) return
  websitePollTimer = window.setTimeout(() => {
    websitePollTimer = null
    loadWebsiteMonitors({ silent: true })
  }, 3000)
}

const loadWebsiteMonitors = async (options = {}) => {
  const silent = options?.silent === true
  if (!silent) websiteListLoading.value = true
  try {
    const res = await getGeoWebsiteMonitorPage({
      pageNum: websitePage.value.pageNum,
      pageSize: websitePage.value.pageSize
    })
    if (res.success) {
      websiteMonitors.value = (res.data || []).map(normalizeWebsiteMonitorRow)
      websitePage.value.total = Number(res.total) || 0
    } else if (!silent) {
      ElMessage.error(res.message || '官网监测列表加载失败')
    }
  } catch {
    if (!silent) ElMessage.error('官网监测列表加载失败')
  } finally {
    if (!silent) websiteListLoading.value = false
    scheduleWebsitePolling()
  }
}

const reloadWebsiteMonitors = () => {
  websitePage.value.pageNum = 1
  loadWebsiteMonitors()
}

const openOfficialSiteMonitor = () => {
  websiteCreateForm.value = newWebsiteCreateForm()
  websitePage.value.pageNum = 1
  showWebsiteManagerDialog.value = true
  loadWebsiteMonitors()
}

const submitWebsiteMonitor = async () => {
  const hospitalId = websiteCreateForm.value.hospitalId
  const siteUrl = normalizeWebsiteUrl(websiteCreateForm.value.siteUrl)
  if (!hospitalId) {
    ElMessage.warning('请选择关联医院')
    return
  }
  if (!siteUrl) {
    ElMessage.warning('请填写有效的 HTTP 或 HTTPS 官网 URL')
    return
  }

  const hospital = hospitalOptions.value.find(item => String(item.id) === String(hospitalId))
  const siteName = String(websiteCreateForm.value.siteName || '').trim()
    || `${hospital?.hospitalName || '医院'}官网`

  savingWebsite.value = true
  try {
    const res = await createGeoWebsiteMonitor({
      hospitalId,
      siteName,
      siteUrl,
      monitorIntervalHours: Math.max(1, Math.min(720, toNumber(websiteCreateForm.value.monitorIntervalHours, 24))),
      runImmediately: Boolean(websiteCreateForm.value.runImmediately)
    })
    if (res.success) {
      ElMessage.success(websiteCreateForm.value.runImmediately ? '官网监测已添加，检测任务已提交' : '官网监测已添加')
      websiteCreateForm.value = newWebsiteCreateForm()
      websitePage.value.pageNum = 1
      await loadWebsiteMonitors()
    } else {
      ElMessage.error(res.message || '官网监测添加失败')
    }
  } catch {
    ElMessage.error('官网监测添加失败')
  } finally {
    savingWebsite.value = false
  }
}

const updateWebsiteSchedule = async (row, value) => {
  const previous = toNumber(row.monitorEnabled)
  const monitorEnabled = toNumber(value) === 1 ? 1 : 0
  row.monitorEnabled = monitorEnabled
  updatingWebsiteId.value = row.id
  try {
    const res = await updateGeoWebsiteMonitor(row.id, { monitorEnabled })
    if (res.success) {
      Object.assign(row, normalizeWebsiteMonitorRow(res.data || { ...row, monitorEnabled }))
      ElMessage.success(monitorEnabled === 1 ? '已启用定时检测' : '已暂停定时检测')
    } else {
      row.monitorEnabled = previous
      ElMessage.error(res.message || '定时检测设置失败')
    }
  } catch {
    row.monitorEnabled = previous
    ElMessage.error('定时检测设置失败')
  } finally {
    updatingWebsiteId.value = null
  }
}

const runWebsiteCheck = async (row) => {
  runningWebsiteId.value = row.id
  try {
    const res = await runGeoWebsiteCheck(row.id)
    if (res.success) {
      row.latestCheckId = res.data?.id || row.latestCheckId
      row.lastCheckStatus = res.data?.status ?? 0
      ElMessage.success('官网检测任务已提交')
      scheduleWebsitePolling()
      await loadWebsiteMonitors({ silent: true })
    } else {
      ElMessage.error(res.message || '官网检测启动失败')
    }
  } catch {
    ElMessage.error('官网检测启动失败')
  } finally {
    runningWebsiteId.value = null
  }
}

const openWebsiteReport = async (row) => {
  websiteReportMonitor.value = row
  websiteReport.value = null
  websiteReportLoading.value = true
  showWebsiteReportDialog.value = true
  try {
    const res = await getGeoWebsiteReport(row.id)
    if (res.success) {
      websiteReport.value = res.data || null
    } else {
      ElMessage.warning(res.message || '暂无官网检测报告')
    }
  } catch {
    ElMessage.warning('暂无官网检测报告，请先运行官网检测')
  } finally {
    websiteReportLoading.value = false
  }
}

const openManualQuery = () => {
  manualForm.value = { sourceType: 2, priority: 50, monitorIntervalHours: 24, enabled: 1, region: '杭州' }
  showManualDialog.value = true
}

const syncManualDepartment = (id) => {
  const dept = departmentOptions.value.find(item => item.id === id)
  manualForm.value.departmentName = dept?.departmentName || ''
  manualForm.value.hospitalId = dept?.hospitalId || manualForm.value.hospitalId
  manualForm.value.hospitalName = dept?.hospitalName || manualForm.value.hospitalName
}

const syncManualDoctor = (id) => {
  const doctor = doctorOptions.value.find(item => item.id === id)
  if (!doctor) return
  manualForm.value.doctorName = doctor.doctorName
  manualForm.value.departmentId = doctor.departmentId
  manualForm.value.departmentName = doctor.departmentName
  manualForm.value.hospitalId = doctor.hospitalId
  manualForm.value.hospitalName = doctor.hospitalName
}

const submitManualQuery = async () => {
  if (!manualForm.value.queryText) {
    ElMessage.warning('请填写问题')
    return
  }
  savingManual.value = true
  try {
    const res = await addGeoQuery(manualForm.value)
    if (res.success) {
      ElMessage.success('问题已保存')
      showManualDialog.value = false
      await loadQueries()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    savingManual.value = false
  }
}

const runMonitorForQuery = async (row) => {
  monitoringQueryId.value = row.id
  try {
    const res = await runGeoMonitor({
      queryId: row.id,
      engines: monitorRunEngines.value.length ? monitorRunEngines.value : undefined
    })
    if (res.success) {
      ElMessage.success(`新增 ${res.data?.length || 0} 条监测记录`)
      await Promise.all([loadQueries(), loadMonitorRecords(), loadMatrix(), loadTrend(), loadSources(), loadMonitorTasks(), loadFeedback(), loadSiteRecommendations()])
    } else {
      ElMessage.error(res.message || '监测失败')
    }
  } catch {
    ElMessage.error('监测失败，请检查 GEO 引擎配置')
  } finally {
    monitoringQueryId.value = null
  }
}

const runMonitorBatch = async () => {
  runningBatch.value = true
  try {
    const res = await runGeoMonitor({
      limit: 20,
      engines: monitorRunEngines.value.length ? monitorRunEngines.value : undefined
    })
    if (res.success) {
      ElMessage.success(`新增 ${res.data?.length || 0} 条监测记录`)
      await Promise.all([loadQueries(), loadMonitorRecords(), loadMatrix(), loadTrend(), loadSources(), loadMonitorTasks(), loadFeedback(), loadSiteRecommendations()])
    } else {
      ElMessage.error(res.message || '运行失败')
    }
  } catch {
    ElMessage.error('运行失败，请检查 GEO 引擎配置')
  } finally {
    runningBatch.value = false
  }
}

const loadMonitorRecords = async () => {
  monitorLoading.value = true
  try {
    const res = await getGeoMonitorRecordPage({
      pageNum: monitorPage.value.pageNum,
      pageSize: monitorPage.value.pageSize,
      engine: monitorFilter.value.engine || undefined,
      mentioned: monitorFilter.value.mentioned === null ? undefined : monitorFilter.value.mentioned,
      cited: monitorFilter.value.cited === null ? undefined : monitorFilter.value.cited
    })
    if (res.success) {
      monitorRecords.value = res.data || []
      monitorPage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '监测记录加载失败')
    }
  } catch {
    ElMessage.error('监测记录加载失败')
  } finally {
    monitorLoading.value = false
  }
}

const reloadMonitorRecords = () => {
  monitorPage.value.pageNum = 1
  loadMonitorRecords()
}

const openRecord = (row) => {
  detailTitle.value = '监测详情'
  detailText.value = JSON.stringify(row, null, 2)
  showDetailDialog.value = true
}

const openGenerateFeedback = async (row) => {
  generatingFeedback.value = true
  try {
    const res = await generateGeoFeedback({ queryId: row.id, limit: 10 })
    if (res.success) {
      ElMessage.success(`生成 ${res.data?.length || 0} 条建议`)
      activeTab.value = 'tasks'
      await Promise.all([loadFeedback(), loadMonitorTasks(), loadSiteRecommendations()])
    } else {
      ElMessage.error(res.message || '生成建议失败')
    }
  } catch {
    ElMessage.error('生成建议失败，请检查模型配置')
  } finally {
    generatingFeedback.value = false
  }
}

const generateFeedbackBatch = async () => {
  generatingFeedback.value = true
  try {
    const res = await generateGeoFeedback({ limit: 20 })
    if (res.success) {
      ElMessage.success(`生成 ${res.data?.length || 0} 条建议`)
      await Promise.all([loadFeedback(), loadMonitorTasks(), loadSiteRecommendations()])
    } else {
      ElMessage.error(res.message || '生成建议失败')
    }
  } catch {
    ElMessage.error('生成建议失败，请检查模型配置')
  } finally {
    generatingFeedback.value = false
  }
}

const loadFeedback = async () => {
  feedbackLoading.value = true
  try {
    const res = await getGeoFeedbackPage({
      pageNum: feedbackPage.value.pageNum,
      pageSize: feedbackPage.value.pageSize,
      queryId: feedbackFilter.value.queryId || undefined,
      status: feedbackFilter.value.status === null ? undefined : feedbackFilter.value.status
    })
    if (res.success) {
      feedbackList.value = res.data || []
      feedbackPage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '反馈建议加载失败')
    }
  } catch {
    ElMessage.error('反馈建议加载失败')
  } finally {
    feedbackLoading.value = false
  }
}

const reloadFeedback = () => {
  feedbackPage.value.pageNum = 1
  loadFeedback()
}

const loadMonitorTasks = async () => {
  taskLoading.value = true
  try {
    const res = await getGeoMonitorTaskPage({
      pageNum: taskPage.value.pageNum,
      pageSize: taskPage.value.pageSize,
      status: taskFilter.value.status === null ? undefined : taskFilter.value.status,
      engine: taskFilter.value.engine || undefined,
      triggerType: taskFilter.value.triggerType || undefined
    })
    if (res.success) {
      taskList.value = res.data || []
      taskPage.value.total = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '队列任务加载失败')
    }
  } catch {
    ElMessage.error('队列任务加载失败')
  } finally {
    taskLoading.value = false
  }
}

const reloadMonitorTasks = () => {
  taskPage.value.pageNum = 1
  loadMonitorTasks()
}

const openFeedback = (row) => {
  detailTitle.value = '反馈建议'
  detailText.value = JSON.stringify(row, null, 2)
  showDetailDialog.value = true
}

const openFeedbackHandling = (row) => {
  if (feedbackActionLoading.value) return
  handlingFeedback.value = row
  feedbackHandlingForm.value = { status: toNumber(row.status), note: row.processingNote || '' }
  feedbackPublishedUrl.value = row.manualPublishedUrl || ''
  showFeedbackHandlingDialog.value = true
}

const applyFeedbackAction = async (action, successMessage) => {
  if (!handlingFeedback.value || feedbackActionLoading.value) return
  const id = handlingFeedback.value.id
  feedbackActionLoading.value = true
  try {
    const res = await action(id)
    if (!res.success) throw new Error(res.message || '操作失败，请重试')
    if (handlingFeedback.value?.id === id) {
      Object.assign(handlingFeedback.value, res.data || {})
      ElMessage.success(successMessage)
      await Promise.all([loadFeedback(), loadMonitorTasks()])
    }
  } catch (error) {
    ElMessage.error(error?.message || '操作失败，请重试')
  } finally {
    feedbackActionLoading.value = false
  }
}

const saveFeedbackHandling = () => {
  if (feedbackHandlingForm.value.status === 1 && !feedbackHandlingForm.value.note.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  return applyFeedbackAction(id => updateGeoFeedbackHandling(id, feedbackHandlingForm.value), '处理记录已保存')
}

const confirmFeedbackPublication = async () => {
  const publishedUrl = feedbackPublishedUrl.value.trim()
  if (!/^https?:\/\//i.test(publishedUrl)) { ElMessage.warning('请填写已发布内容的HTTP或HTTPS链接'); return }
  try {
    await ElMessageBox.confirm('确认该内容已由您在外部平台发布？本操作只登记发布证据。', '登记外部发布', { type: 'warning' })
  } catch { return }
  return applyFeedbackAction(id => confirmGeoFeedbackPublication(id, { publishedUrl }), '已登记人工发布证据')
}

const retestFeedback = () => applyFeedbackAction(id => requestGeoFeedbackRetest(id), '下一次复测已加入执行队列')

const refreshHandlingFeedback = async () => {
  const id = handlingFeedback.value?.id
  if (!id || feedbackActionLoading.value) return
  feedbackActionLoading.value = true
  try {
    const res = await getGeoFeedbackDetail(id)
    if (!res.success) throw new Error(res.message || '状态加载失败')
    if (handlingFeedback.value?.id === id) Object.assign(handlingFeedback.value, res.data || {})
    await Promise.all([loadFeedback(), loadMonitorTasks()])
  } catch (error) {
    ElMessage.error(error?.message || '状态加载失败')
  } finally {
    feedbackActionLoading.value = false
  }
}

const auditText = async () => {
  if (!complianceForm.value.title && !complianceForm.value.content) {
    ElMessage.warning('请填写标题或正文')
    return
  }
  auditingText.value = true
  try {
    const res = await auditTextCompliance(complianceForm.value)
    if (res.success) {
      complianceResult.value = res.data
      ElMessage.success(res.data?.passed ? '合规审核通过' : '发现合规风险')
    } else {
      ElMessage.error(res.message || '审核失败')
    }
  } catch {
    ElMessage.error('审核失败')
  } finally {
    auditingText.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  await Promise.all([
    loadQueries(),
    loadMatrix(),
    loadTrend(),
    loadSources(),
    loadProbes(),
    loadSiteRecommendations(),
    loadMonitorRecords(),
    loadFeedback(),
    loadMonitorTasks()
  ])
  void detailPayload.value
})

onUnmounted(() => {
  stopWebsitePolling()
})
</script>

<style scoped>
.geo-workbench-page {
  max-width: 1440px;
  margin: 0 auto;
}

.geo-tabs {
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 12px 16px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.panel-card {
  padding-top: 8px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter {
  width: 180px;
}

.filter-sm {
  width: 120px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.website-create-panel,
.website-list-panel {
  min-width: 0;
}

.website-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.website-section-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.website-section-heading strong {
  color: #303133;
  font-size: 15px;
}

.website-section-heading span {
  color: #909399;
  font-size: 12px;
}

.website-create-form {
  display: grid;
  grid-template-columns: 1.1fr 1.1fr 1.8fr 120px;
  gap: 0 12px;
  align-items: end;
}

.website-create-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.website-create-actions {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
}

.website-list-heading {
  margin-bottom: 10px;
}

.website-name-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}

.website-name-cell strong,
.website-name-cell a,
.website-name-cell span,
.website-name-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.website-name-cell a {
  color: #409eff;
  font-size: 12px;
  text-decoration: none;
}

.website-name-cell a:hover {
  text-decoration: underline;
}

.website-name-cell span,
.website-name-cell small {
  color: #909399;
  font-size: 11px;
}

.website-score-cell {
  display: grid;
  grid-template-columns: 48px 24px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #606266;
  font-size: 12px;
}

.website-pagination {
  margin-top: 12px;
}

.website-report {
  display: flex;
  max-height: 76vh;
  overflow-y: auto;
  flex-direction: column;
  gap: 18px;
  padding-right: 8px;
}

.website-report-overview {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  align-items: center;
  gap: 20px;
  padding: 14px 18px;
  border: 1px solid #e8eef8;
  border-radius: 10px;
  background: linear-gradient(135deg, #f8fbff, #f5f3ff);
}

.website-report-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 18px;
  color: #606266;
  font-size: 13px;
}

.website-report-meta h3,
.website-report-meta a,
.website-report-error {
  grid-column: 1 / -1;
}

.website-report-meta h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
}

.website-report-meta a {
  overflow: hidden;
  color: #409eff;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.website-report-error {
  color: #f56c6c;
}

.website-report-heading {
  margin-bottom: 10px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.website-citation-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.website-citation-grid > div {
  display: flex;
  min-width: 0;
  min-height: 72px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  background: #fafbfc;
  text-align: center;
}

.website-citation-grid strong {
  max-width: 100%;
  color: #303133;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.website-citation-grid span {
  color: #909399;
  font-size: 12px;
}

.website-recommendations ol {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 24px;
  color: #606266;
  font-size: 13px;
  line-height: 1.55;
}

.compliance-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.8fr);
  gap: 16px;
}

.result-card {
  border: 1px solid #eef0f5;
  border-radius: 8px;
  padding: 14px;
  min-width: 0;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.risk-level {
  color: #606266;
  margin-bottom: 12px;
  font-size: 13px;
}

.score-cell {
  display: grid;
  grid-template-columns: 48px 28px;
  gap: 6px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #606266;
}

.platform-cell {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  justify-content: center;
  gap: 6px 8px;
  font-size: 12px;
  color: #606266;
  line-height: 1.3;
}

.trend-cell {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 36px;
  align-items: center;
  gap: 10px;
}

.muted {
  color: #a8abb2;
  font-size: 12px;
}

.persist-check {
  margin-left: 16px;
}

.json-view {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60vh;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .compliance-grid {
    grid-template-columns: 1fr;
  }

  .website-create-form,
  .website-citation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .website-url-field {
    grid-column: 1 / -1;
  }

  .website-report-overview {
    grid-template-columns: 130px minmax(0, 1fr);
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left,
  .toolbar-right,
  .filter,
  .filter-sm {
    width: 100%;
  }
}
</style>
