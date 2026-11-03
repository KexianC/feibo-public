import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import Layout from "@/components/Layout";
import ProtectedRoute from "@/components/ProtectedRoute";
import AdminRoutePolicyGuard from "@/components/AdminRoutePolicyGuard";
import { useAuth } from "@/hooks/useAuth";
import Login from "@/pages/Login";
import FeiboLogin from "@/pages/FeiboLogin";
import Dashboard from "@/pages/Dashboard";
import UserList from "@/pages/users/UserList";
import UserManage from "@/pages/users/UserManage";
import Conversations from "@/pages/users/Conversations";
import SynthesisRecords from "@/pages/users/SynthesisRecords";
import UserFeedback from "@/pages/users/UserFeedback";
import OrderList from "@/pages/orders/OrderList";
import OrderManage from "@/pages/orders/OrderManage";
import MembershipEntitlements from "@/pages/packages/MembershipEntitlements";
import DefaultCategories from "@/pages/functions/DefaultCategories";
import ContentReview from "@/pages/functions/ContentReview";
import ExpertReview from "@/pages/functions/ExpertReview";
import VoiceManage from "@/pages/functions/VoiceManage";
import BackgroundManage from "@/pages/functions/BackgroundManage";
import MusicManage from "@/pages/functions/MusicManage";
import VideoManage from "@/pages/functions/VideoManage";
import IllustrationManage from "@/pages/functions/IllustrationManage";
import ModelManage from "@/pages/functions/ModelManage";
import TemplateManage from "@/pages/functions/TemplateManage";
import CaseVideos from "@/pages/functions/CaseVideos";
import GeoAdminFrame from "@/pages/geo/GeoAdminFrame";
import AgentList from "@/pages/market/AgentList";
import AgentManage from "@/pages/market/AgentManage";
import AgentLevel from "@/pages/market/AgentLevel";
import AgentRelation from "@/pages/market/AgentRelation";
import AgentApproval from "@/pages/market/AgentApproval";
import RecruitmentSettings from "@/pages/market/RecruitmentSettings";
import RecruitmentRelations from "@/pages/market/RecruitmentRelations";
import ManagementFeeSettings from "@/pages/market/ManagementFeeSettings";
import RebateSettings from "@/pages/market/RebateSettings";
import PartnershipSubsidy from "@/pages/market/PartnershipSubsidy";
import PartnerList from "@/pages/market/PartnerList";
import PartnerSettings from "@/pages/market/PartnerSettings";
import PartnerRelations from "@/pages/market/PartnerRelations";
import PartnerApproval from "@/pages/market/PartnerApproval";
import OperationFeeIssue from "@/pages/finance/OperationFeeIssue";
import OperationFeeReview from "@/pages/finance/OperationFeeReview";
import RecruitmentFeeIssue from "@/pages/finance/RecruitmentFeeIssue";
import RecruitmentFeeReview from "@/pages/finance/RecruitmentFeeReview";
import RegionalFeeIssue from "@/pages/finance/RegionalFeeIssue";
import RegionalFeeReview from "@/pages/finance/RegionalFeeReview";
import LargeRegionalFeeIssue from "@/pages/finance/LargeRegionalFeeIssue";
import LargeRegionalFeeReview from "@/pages/finance/LargeRegionalFeeReview";
import PartnerCommission from "@/pages/finance/PartnerCommission";
import AccountRoles from "@/pages/accounts/AccountRoles";
import AccountList from "@/pages/accounts/AccountList";
import SystemInfo from "@/pages/system/SystemInfo";
import AiModelManagement from "@/pages/system/AiModelManagement";
import VideoGenerationManagement from "@/pages/system/VideoGenerationManagement";
import OperationLog from "@/pages/logs/OperationLog";
import LoginLog from "@/pages/logs/LoginLog";

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Router>
        <Routes>
          {/* 登录页面 - 不需要Layout包装 */}
          <Route path="/login" element={<FeiboLogin />} />
          <Route path="/web/login" element={<FeiboLogin />} />
          
          {/* 其他页面 - 需要Layout包装和路由保护 */}
          <Route path="/*" element={
            <ProtectedRoute>
              <AdminRoutePolicyGuard>
                <Layout>
                <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/dashboard" element={<Dashboard />} />
            
            {/* 用户管理 */}
            <Route path="/users/list" element={<UserList />} />
            <Route path="/users/manage" element={<UserManage />} />
            <Route path="/users/conversations" element={<Conversations />} />
            <Route path="/users/synthesis" element={<SynthesisRecords />} />
            <Route path="/users/feedback" element={<UserFeedback />} />
            
            {/* 订单管理 */}
            <Route path="/orders/list" element={<OrderList />} />
            <Route path="/orders/manage" element={<OrderManage />} />
            
            {/* 套餐管理 */}
            <Route path="/packages/entitlements" element={<MembershipEntitlements />} />
            <Route path="/packages/plans" element={<MembershipEntitlements />} />
            <Route path="/packages/compute" element={<MembershipEntitlements />} />
            <Route path="/packages/grants" element={<MembershipEntitlements />} />
            
            {/* 功能管理 - 文案助手 */}
            <Route path="/functions/copywriting/categories" element={<DefaultCategories />} />
            
            {/* 功能管理 - 文案审核 */}
            <Route path="/functions/review/content" element={<ContentReview />} />
            <Route path="/functions/review/expert" element={<ExpertReview />} />
            <Route path="/functions/review/platform" element={<Navigate to="/functions/review/expert" replace />} />
            
            {/* 功能管理 - 工作台 */}
            <Route path="/functions/workspace/voice" element={<VoiceManage />} />
            <Route path="/functions/workspace/background" element={<BackgroundManage />} />
            <Route path="/functions/workspace/music" element={<MusicManage />} />
            <Route path="/functions/workspace/video" element={<VideoManage />} />
            <Route path="/functions/workspace/illustration" element={<IllustrationManage />} />
            <Route path="/functions/workspace/model" element={<ModelManage />} />
            <Route path="/functions/workspace/template" element={<TemplateManage />} />
            <Route path="/functions/workspace/cases" element={<CaseVideos />} />

            {/* GEOAgent */}
            <Route path="/geo/medical-management" element={<GeoAdminFrame modulePath="medicalManagement" title="医务管理" />} />
            <Route path="/geo/hospital-management" element={<GeoAdminFrame modulePath="hospitalManagement" title="医院管理" />} />
            <Route path="/geo/department-management" element={<GeoAdminFrame modulePath="departmentManagement" title="科室管理" />} />
            <Route path="/geo/doctor-management" element={<GeoAdminFrame modulePath="doctorManagement" title="医生管理" />} />
            <Route path="/geo/knowledge-base" element={<GeoAdminFrame modulePath="knowledgeBase" title="知识库管理" />} />
            <Route path="/geo/workbench" element={<GeoAdminFrame modulePath="geoWorkbench" title="GEO运营" />} />
            <Route path="/geo/content-publish" element={<GeoAdminFrame modulePath="contentPublish" title="内容发布" />} />
            <Route path="/geo/vector-conversion" element={<GeoAdminFrame modulePath="vectorConversion" title="向量转换" />} />
            <Route path="/geo/article-generator" element={<GeoAdminFrame modulePath="articleGenerator" title="AI文章生成" />} />
            <Route path="/geo/article-management" element={<GeoAdminFrame modulePath="articleManagement" title="文章管理" />} />
            <Route path="/geo/account-management" element={<GeoAdminFrame modulePath="accountManagement" title="账号管理" />} />
            <Route path="/geo/publish-record" element={<GeoAdminFrame modulePath="publishRecord" title="发布记录" />} />
            
            {/* 市场管理 - 代理 */}
            <Route path="/market/agents/list" element={<AgentList />} />
            <Route path="/market/agents/manage" element={<AgentManage />} />
            <Route path="/market/agents/level" element={<AgentLevel />} />
            <Route path="/market/agents/relation" element={<AgentRelation />} />
            <Route path="/market/agents/approval" element={<AgentApproval />} />
            
            {/* 市场管理 - 招商 */}
            <Route path="/market/recruitment/settings" element={<RecruitmentSettings />} />
            <Route path="/market/recruitment/relations" element={<RecruitmentRelations />} />
            
            {/* 市场管理 - 运营 */}
            <Route path="/market/operations/management-fee" element={<ManagementFeeSettings />} />
            <Route path="/market/operations/rebate" element={<RebateSettings />} />
            <Route path="/market/operations/partnership-subsidy" element={<PartnershipSubsidy />} />
            
            {/* 市场管理 - 合伙 */}
            <Route path="/market/partners/list" element={<PartnerList />} />
            <Route path="/market/partners/settings" element={<PartnerSettings />} />
            <Route path="/market/partners/relations" element={<PartnerRelations />} />
            <Route path="/market/partners/approval" element={<PartnerApproval />} />
            
            {/* 财务管理 - 市场费用 */}
            <Route path="/finance/market-expenses/operation-fee-issue" element={<OperationFeeIssue />} />
            <Route path="/finance/market-expenses/operation-fee-review" element={<OperationFeeReview />} />
            <Route path="/finance/market-expenses/recruitment-fee-issue" element={<RecruitmentFeeIssue />} />
            <Route path="/finance/market-expenses/recruitment-fee-review" element={<RecruitmentFeeReview />} />
            <Route path="/finance/market-expenses/regional-fee-issue" element={<RegionalFeeIssue />} />
            <Route path="/finance/market-expenses/regional-fee-review" element={<RegionalFeeReview />} />
            <Route path="/finance/market-expenses/large-regional-fee-issue" element={<LargeRegionalFeeIssue />} />
            <Route path="/finance/market-expenses/large-regional-fee-review" element={<LargeRegionalFeeReview />} />
            
            {/* 财务管理 - 合伙佣金 */}
            <Route path="/finance/partner-commission" element={<PartnerCommission />} />
            
            {/* 账户管理 */}
            <Route path="/accounts/roles" element={<AccountRoles />} />
            <Route path="/accounts/list" element={<AccountList />} />
            
            {/* 系统管理 */}
            <Route path="/system/info" element={<SystemInfo />} />
            <Route path="/system/ai-models" element={<AiModelManagement />} />
            <Route path="/system/video-generation" element={<VideoGenerationManagement />} />
            
            {/* 日志管理 */}
            <Route path="/logs/operation" element={<OperationLog />} />
            <Route path="/logs/login" element={<LoginLog />} />
            
                </Routes>
                </Layout>
              </AdminRoutePolicyGuard>
            </ProtectedRoute>
          } />
        </Routes>
      </Router>
    </ConfigProvider>
  );
}
