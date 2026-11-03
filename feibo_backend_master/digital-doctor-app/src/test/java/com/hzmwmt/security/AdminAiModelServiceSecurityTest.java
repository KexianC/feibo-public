package com.hzmwmt.security;

import com.hzmwmt.domain.ai.adapter.port.IAIModelRuntimeManager;
import com.hzmwmt.domain.dto.AdminAiModelCreateDTO;
import com.hzmwmt.domain.dto.AdminAiModelUpdateDTO;
import com.hzmwmt.domain.entity.user.TAiModelConfig;
import com.hzmwmt.domain.mapper.user.AdminModelConfigMapper;
import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.domain.service.user.impl.AdminModelConfigServiceImpl;
import com.hzmwmt.domain.vo.AdminAiModelVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiModelServiceSecurityTest {

    private static final long MODEL_ID = 9005300000000000001L;
    private AdminModelConfigMapper mapper;
    private IAIModelRuntimeManager runtimeManager;
    private AIModelApiKeyCipher cipher;
    private AdminModelConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminModelConfigMapper.class);
        runtimeManager = mock(IAIModelRuntimeManager.class);
        cipher = new AIModelApiKeyCipher(Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        service = new AdminModelConfigServiceImpl();
        ReflectionTestUtils.setField(service, "adminModelConfigMapper", mapper);
        ReflectionTestUtils.setField(service, "apiKeyCipher", cipher);
        ReflectionTestUtils.setField(service, "runtimeManager", runtimeManager);
        when(mapper.lockManagedGeneralRootForUpdate()).thenReturn(1L);
        when(mapper.lockManagedProviderScopeForUpdate(anyLong())).thenReturn(12L);
        when(mapper.countManagedProviderScopesForModelCode("kimik3"))
                .thenReturn(1);
    }

    @Test
    void blankApiKeyRetainsExistingValueWithoutWriting() {
        TAiModelConfig existing = existingModel();
        existing.setApiKeyCiphertext(cipher.encrypt(MODEL_ID, "old-key"));
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing);

        AdminAiModelVO result = service.updateApiKey(MODEL_ID, "   ");

        verify(mapper, never()).updateManagedLeafApiKey(
                anyLong(), anyLong(), any());
        verify(runtimeManager, never()).invalidate(any());
        assertThat(result.getApiKeyConfigured()).isTrue();
        assertThat(result.getApiKeyMasked()).isEqualTo("********");
    }

    @Test
    void apiKeyUpdatePersistsOnlyCiphertextAndInvalidatesRuntimeModel() {
        TAiModelConfig existing = existingModel();
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing);
        when(runtimeManager.isSupportedProvider("kimik3")).thenReturn(true);
        when(mapper.updateManagedLeafApiKey(
                anyLong(), anyLong(), any())).thenReturn(1);

        AdminAiModelVO result = service.updateApiKey(MODEL_ID, "new-provider-key");

        ArgumentCaptor<String> update = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateManagedLeafApiKey(
                eq(MODEL_ID), eq(12L), update.capture());
        String stored = update.getValue();
        assertThat(stored).startsWith("v1:").doesNotContain("new-provider-key");
        assertThat(cipher.decrypt(MODEL_ID, stored)).isEqualTo("new-provider-key");
        verify(runtimeManager).invalidate(MODEL_ID);
        assertThat(result.getApiKeyMasked()).isEqualTo("********");
    }

    @Test
    void createAlwaysPersistsHiddenAndKeepsDisplayCopyOutOfRuntimeDescription() {
        TAiModelConfig providerTemplate = existingModel();
        providerTemplate.setParentId(12L);
        when(runtimeManager.isSupportedProvider("kimik3")).thenReturn(true);
        when(mapper.selectProviderTemplate("kimik3")).thenReturn(providerTemplate);
        when(mapper.selectManagedSiblingModelsForUpdate(12L)).thenReturn(List.of());
        when(mapper.insert(any(TAiModelConfig.class))).thenReturn(1);

        AdminAiModelCreateDTO request = new AdminAiModelCreateDTO();
        request.setModelCode("kimik3");
        request.setName("Kimi K3 Preview");
        request.setModelName("kimi-k3-preview");
        request.setDescription("Public introduction");
        request.setVisible(false);

        AdminAiModelVO result = service.createManagedModel(request);

        ArgumentCaptor<TAiModelConfig> insert = ArgumentCaptor.forClass(TAiModelConfig.class);
        verify(mapper).insert(insert.capture());
        assertThat(insert.getValue().getVisible()).isFalse();
        assertThat(insert.getValue().getDisplayDescription())
                .isEqualTo("Public introduction");
        assertThat(insert.getValue().getDescription()).isNull();
        assertThat(result.getVisible()).isFalse();
        assertThat(result.getDescription()).isEqualTo("Public introduction");
        assertThat(result.getProviderId()).isEqualTo("9005300000000000000");
        assertThat(result.getProviderName()).isEqualTo("Kimi");
        assertThat(result.getProviderOrder()).isEqualTo(3);
        assertThat(result.getOrder()).isEqualTo(1);
    }

    @Test
    void createRejectsAnAttemptToPublishImmediately() {
        AdminAiModelCreateDTO request = new AdminAiModelCreateDTO();
        request.setModelCode("kimik3");
        request.setName("Kimi K3 Preview");
        request.setModelName("kimi-k3-preview");
        request.setVisible(true);

        assertThatThrownBy(() -> service.createManagedModel(request))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class);
        verify(mapper, never()).insert(any(TAiModelConfig.class));
    }

    @Test
    void createFailsClosedWhenOneModelCodeMapsToMultiplePhysicalProviders() {
        when(runtimeManager.isSupportedProvider("kimik3")).thenReturn(true);
        when(mapper.countManagedProviderScopesForModelCode("kimik3"))
                .thenReturn(2);

        AdminAiModelCreateDTO request = new AdminAiModelCreateDTO();
        request.setModelCode("kimik3");
        request.setName("Kimi K3 Preview");
        request.setModelName("kimi-k3-preview");
        request.setVisible(false);

        assertThatThrownBy(() -> service.createManagedModel(request))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("多个供应商");

        verify(mapper, never()).selectProviderTemplate("kimik3");
        verify(mapper, never()).insert(any(TAiModelConfig.class));
    }

    @Test
    void createAtASelectedPositionShiftsAndNormalizesExistingVersions() {
        TAiModelConfig providerTemplate = existingModel();
        TAiModelConfig first = siblingModel(MODEL_ID - 2, 1);
        TAiModelConfig duplicateFirst = siblingModel(MODEL_ID - 1, 1);
        when(runtimeManager.isSupportedProvider("kimik3")).thenReturn(true);
        when(mapper.selectProviderTemplate("kimik3")).thenReturn(providerTemplate);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(duplicateFirst, first));
        when(mapper.insert(any(TAiModelConfig.class))).thenReturn(1);
        when(mapper.updateManagedModelOrder(anyLong(), anyLong(), anyInt()))
                .thenReturn(1);

        AdminAiModelCreateDTO request = new AdminAiModelCreateDTO();
        request.setModelCode("kimik3");
        request.setName("Kimi K3 Preview");
        request.setModelName("kimi-k3-preview");
        request.setVisible(false);
        request.setOrder(2);

        AdminAiModelVO result = service.createManagedModel(request);

        assertThat(result.getOrder()).isEqualTo(2);
        verify(mapper).updateManagedModelOrder(MODEL_ID - 1, 12L, 3);
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).lockManagedProviderScopeForUpdate(12L);
        lockOrder.verify(mapper).selectManagedSiblingModelsForUpdate(12L);
        lockOrder.verify(mapper).insert(any(TAiModelConfig.class));
    }

    @Test
    void createRejectsASelectedPositionBeyondTheInsertionRange() {
        TAiModelConfig providerTemplate = existingModel();
        TAiModelConfig onlySibling = siblingModel(MODEL_ID - 1, 1);
        when(runtimeManager.isSupportedProvider("kimik3")).thenReturn(true);
        when(mapper.selectProviderTemplate("kimik3")).thenReturn(providerTemplate);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(onlySibling));

        AdminAiModelCreateDTO request = new AdminAiModelCreateDTO();
        request.setModelCode("kimik3");
        request.setName("Kimi K3 Preview");
        request.setModelName("kimi-k3-preview");
        request.setVisible(false);
        request.setOrder(3);

        assertThatThrownBy(() -> service.createManagedModel(request))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("1 到 2");

        verify(mapper, never()).insert(any(TAiModelConfig.class));
        verify(mapper, never()).updateManagedModelOrder(anyLong(), anyLong(), anyInt());
    }

    @Test
    void descriptionUpdateNeverChangesLegacyRuntimePrompt() {
        TAiModelConfig existing = existingModel();
        existing.setDescription("legacy-system-prompt");
        existing.setDisplayDescription("old-public-copy");
        TAiModelConfig refreshed = existingModel();
        refreshed.setDescription("legacy-system-prompt");
        refreshed.setDisplayDescription("new-public-copy");
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing, refreshed);
        when(mapper.updateManagedLeafMetadata(any(TAiModelConfig.class))).thenReturn(1);

        AdminAiModelUpdateDTO request = new AdminAiModelUpdateDTO();
        request.setDescription("new-public-copy");
        service.updateManagedModel(MODEL_ID, request);

        ArgumentCaptor<TAiModelConfig> update = ArgumentCaptor.forClass(TAiModelConfig.class);
        verify(mapper).updateManagedLeafMetadata(update.capture());
        assertThat(update.getValue().getDisplayDescription())
                .isEqualTo("new-public-copy");
        assertThat(update.getValue().getDescription()).isNull();
    }

    @Test
    void versionMoveWritesEveryChangedSiblingAsAContinuousOneBasedSequence() {
        TAiModelConfig existing = existingModel();
        TAiModelConfig refreshed = existingModel();
        refreshed.setSort(1);
        TAiModelConfig first = siblingModel(MODEL_ID - 1, 1);
        TAiModelConfig second = siblingModel(MODEL_ID + 1, 2);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing, refreshed);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(first, second, existing));
        when(mapper.updateManagedModelOrder(anyLong(), anyLong(), anyInt()))
                .thenReturn(1);

        AdminAiModelUpdateDTO request = new AdminAiModelUpdateDTO();
        request.setOrder(1);
        AdminAiModelVO result = service.updateManagedModel(MODEL_ID, request);

        verify(mapper).updateManagedModelOrder(MODEL_ID, 12L, 1);
        verify(mapper).updateManagedModelOrder(MODEL_ID - 1, 12L, 2);
        verify(mapper).updateManagedModelOrder(MODEL_ID + 1, 12L, 3);
        verify(mapper, never()).updateManagedLeafMetadata(any(TAiModelConfig.class));
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).lockManagedProviderScopeForUpdate(12L);
        lockOrder.verify(mapper).selectManagedSiblingModelsForUpdate(12L);
        assertThat(result.getOrder()).isEqualTo(1);
        assertThat(result.getProviderId()).isEqualTo("9005300000000000000");
        assertThat(result.getProviderOrder()).isEqualTo(3);
    }

    @Test
    void duplicateVersionOrdersAreStablyNormalizedByBusinessId() {
        TAiModelConfig target = existingModel();
        target.setSort(2);
        TAiModelConfig lowerId = siblingModel(MODEL_ID - 2, 1);
        TAiModelConfig higherId = siblingModel(MODEL_ID - 1, 1);
        TAiModelConfig refreshed = existingModel();
        refreshed.setSort(3);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target, refreshed);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(higherId, target, lowerId));
        when(mapper.updateManagedModelOrder(anyLong(), anyLong(), anyInt()))
                .thenReturn(1);

        AdminAiModelUpdateDTO request = new AdminAiModelUpdateDTO();
        request.setOrder(3);
        service.updateManagedModel(MODEL_ID, request);

        verify(mapper).updateManagedModelOrder(MODEL_ID - 1, 12L, 2);
        verify(mapper).updateManagedModelOrder(MODEL_ID, 12L, 3);
        verify(mapper, times(2))
                .updateManagedModelOrder(anyLong(), anyLong(), anyInt());
    }

    @Test
    void versionMoveRejectsPositionsOutsideTheCurrentSiblingRange() {
        TAiModelConfig existing = existingModel();
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(existing));
        AdminAiModelUpdateDTO request = new AdminAiModelUpdateDTO();
        request.setOrder(2);

        assertThatThrownBy(() -> service.updateManagedModel(MODEL_ID, request))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("1 到 1");

        verify(mapper, never()).updateManagedModelOrder(anyLong(), anyLong(), anyInt());
    }

    @Test
    void providerMoveUsesTheNineteenDigitBusinessIdAndReordersEverySibling() {
        long providerId = 9005300000000000000L;
        TAiModelConfig provider = providerModel(providerId, 3);
        TAiModelConfig first = providerModel(providerId - 2, 1);
        TAiModelConfig second = providerModel(providerId - 1, 2);
        TAiModelConfig version = existingModel();
        when(mapper.selectManagedProvidersForUpdate())
                .thenReturn(List.of(second, provider, first));
        when(mapper.updateManagedProviderOrder(anyLong(), anyInt())).thenReturn(1);
        when(mapper.selectManagedModels()).thenReturn(List.of(version));

        List<AdminAiModelVO> result = service.updateProviderOrder(providerId, 1);

        verify(mapper).updateManagedProviderOrder(providerId, 1);
        verify(mapper).updateManagedProviderOrder(providerId - 2, 2);
        verify(mapper).updateManagedProviderOrder(providerId - 1, 3);
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).lockManagedGeneralRootForUpdate();
        lockOrder.verify(mapper).selectManagedProvidersForUpdate();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderId()).isEqualTo("9005300000000000000");
        assertThat(result.get(0).getProviderOrder()).isEqualTo(1);
        assertThat(result.get(0).getOrder()).isEqualTo(4);
    }

    @Test
    void providerOrderUpdateRejectsIdsOutsideTheManagedGeneralRoot() {
        long outOfScopeProviderId = 9005400000000000000L;
        when(mapper.selectManagedProvidersForUpdate()).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateProviderOrder(outOfScopeProviderId, 1))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class);

        verify(mapper, never()).updateManagedProviderOrder(any(), any());
        verify(mapper, never()).selectManagedModels();
    }

    @Test
    void providerMoveRejectsPositionsOutsideTheLockedProviderRange() {
        long providerId = 9005300000000000000L;
        when(mapper.selectManagedProvidersForUpdate())
                .thenReturn(List.of(providerModel(providerId, 1)));

        assertThatThrownBy(() -> service.updateProviderOrder(providerId, 2))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("1 到 1");

        verify(mapper, never()).updateManagedProviderOrder(anyLong(), anyInt());
    }

    @Test
    void providerMoveFailsClosedWhenAnEmptyProviderWouldBeMissingFromTheUi() {
        long providerId = 9005300000000000000L;
        when(mapper.selectManagedProvidersForUpdate())
                .thenReturn(List.of(providerModel(providerId, 1)));
        when(mapper.countManagedEmptyProviders()).thenReturn(1);

        assertThatThrownBy(() -> service.updateProviderOrder(providerId, 1))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("无模型版本");

        verify(mapper, never()).updateManagedProviderOrder(anyLong(), anyInt());
    }

    @Test
    void legacyNullOrdersRemainNullSoClientsCanApplyNullsLast() {
        TAiModelConfig legacy = existingModel();
        legacy.setSort(null);
        legacy.setProviderOrder(null);
        when(mapper.selectManagedModels()).thenReturn(List.of(legacy));

        AdminAiModelVO result = service.listManagedModels().get(0);

        assertThat(result.getOrder()).isNull();
        assertThat(result.getProviderOrder()).isNull();
    }

    @Test
    void visibilityRequiresProviderReadinessAndADecryptableEffectiveKey() {
        TAiModelConfig existing = existingModel();
        existing.setVisible(false);
        existing.setParentId(12L);
        existing.setApiKeyCiphertext(cipher.encrypt(MODEL_ID, "working-key"));
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing);
        when(mapper.selectProviderTemplate("kimik3")).thenReturn(existing);
        when(runtimeManager.isProviderReady("kimik3")).thenReturn(true);
        when(mapper.updateManagedLeafVisibility(
                anyLong(), anyLong(), any())).thenReturn(1);

        AdminAiModelVO result = service.updateVisibility(MODEL_ID, true);

        assertThat(result.getVisible()).isTrue();
        verify(mapper).updateManagedLeafVisibility(MODEL_ID, 12L, true);
    }

    @Test
    void visibilityFailsClosedWhenProviderCategoryOrRuntimeIsNotReady() {
        TAiModelConfig existing = existingModel();
        existing.setVisible(false);
        existing.setApiKeyCiphertext(cipher.encrypt(MODEL_ID, "working-key"));
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(existing);
        when(runtimeManager.isProviderReady("kimik3")).thenReturn(true);

        assertThatThrownBy(() -> service.updateVisibility(MODEL_ID, true))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class);
        verify(mapper, never()).updateManagedLeafVisibility(
                anyLong(), anyLong(), any());
    }

    @Test
    void softDeleteHiddenModelClearsItFromTheActiveOrder() {
        TAiModelConfig target = existingModel();
        target.setVisible(false);
        target.setSort(2);
        TAiModelConfig first = siblingModel(MODEL_ID - 1, 1);
        TAiModelConfig third = siblingModel(MODEL_ID + 1, 3);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(third, target, first));
        when(mapper.softDeleteManagedLeafModel(MODEL_ID, 12L)).thenReturn(1);
        when(mapper.updateManagedModelOrder(anyLong(), anyLong(), anyInt()))
                .thenReturn(1);

        service.deleteManagedModel(MODEL_ID);

        InOrder mutationOrder = inOrder(mapper);
        mutationOrder.verify(mapper).lockManagedProviderScopeForUpdate(12L);
        mutationOrder.verify(mapper).selectManagedSiblingModelsForUpdate(12L);
        mutationOrder.verify(mapper).softDeleteManagedLeafModel(MODEL_ID, 12L);
        mutationOrder.verify(mapper)
                .updateManagedModelOrder(MODEL_ID + 1, 12L, 2);
        verify(runtimeManager).invalidate(MODEL_ID);
    }

    @Test
    void deleteRejectsVisibleModelBeforeTombstoneMutation() {
        TAiModelConfig target = existingModel();
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(target, siblingModel(MODEL_ID + 1, 1)));

        assertThatThrownBy(() -> service.deleteManagedModel(MODEL_ID))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("先隐藏模型");

        verify(mapper, never()).softDeleteManagedLeafModel(anyLong(), anyLong());
        verify(runtimeManager, never()).invalidate(any());
    }

    @Test
    void deleteRejectsTheProvidersLastModelVersion() {
        TAiModelConfig target = existingModel();
        target.setVisible(false);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(target));

        assertThatThrownBy(() -> service.deleteManagedModel(MODEL_ID))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("至少需要保留一个模型版本");

        verify(mapper, never()).softDeleteManagedLeafModel(anyLong(), anyLong());
    }

    @Test
    void softDeleteDoesNotConsultHistoricalReferencesBecauseTheRowIsRetained() {
        TAiModelConfig target = existingModel();
        target.setVisible(false);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(target, siblingModel(MODEL_ID + 1, 1)));
        when(mapper.softDeleteManagedLeafModel(MODEL_ID, 12L)).thenReturn(1);

        service.deleteManagedModel(MODEL_ID);

        verify(mapper).softDeleteManagedLeafModel(MODEL_ID, 12L);
        assertThat(java.util.Arrays.stream(AdminModelConfigMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("countAiMessageReferences", "deleteManagedLeafModel");
        verify(runtimeManager).invalidate(MODEL_ID);
    }

    @Test
    void deleteFailsClosedWhenTombstoneUpdateFindsNoActiveScopedRow() {
        TAiModelConfig target = existingModel();
        target.setVisible(false);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(target);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(target, siblingModel(MODEL_ID + 1, 2)));
        when(mapper.softDeleteManagedLeafModel(MODEL_ID, 12L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteManagedModel(MODEL_ID))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class);

        verify(mapper).softDeleteManagedLeafModel(MODEL_ID, 12L);
        verify(mapper, never()).updateManagedModelOrder(
                anyLong(), anyLong(), anyInt());
        verify(runtimeManager, never()).invalidate(any());
    }

    @Test
    void deleteFailsClosedWhenTargetDisappearsFromTheLockedManagedScope() {
        TAiModelConfig stale = existingModel();
        stale.setVisible(false);
        when(mapper.selectManagedModelById(MODEL_ID)).thenReturn(stale);
        when(mapper.selectManagedSiblingModelsForUpdate(12L))
                .thenReturn(List.of(siblingModel(MODEL_ID + 1, 1)));

        assertThatThrownBy(() -> service.deleteManagedModel(MODEL_ID))
                .isInstanceOf(com.hzmwmt.types.exception.AppException.class)
                .hasMessageContaining("模型不存在");

        verify(mapper, never()).softDeleteManagedLeafModel(anyLong(), anyLong());
    }

    private TAiModelConfig existingModel() {
        TAiModelConfig model = new TAiModelConfig();
        model.setAiModelConfigId(MODEL_ID);
        model.setIsLeaf(true);
        model.setDisplayName("Kimi K3");
        model.setName("kimi-k3");
        model.setModelCode("kimik3");
        model.setVisible(true);
        model.setIsDeleted(false);
        model.setSort(4);
        model.setParentId(12L);
        model.setProviderId(9005300000000000000L);
        model.setProviderName("Kimi");
        model.setProviderOrder(3);
        return model;
    }

    private TAiModelConfig siblingModel(long modelId, int sort) {
        TAiModelConfig model = existingModel();
        model.setAiModelConfigId(modelId);
        model.setSort(sort);
        return model;
    }

    private TAiModelConfig providerModel(long providerId, int sort) {
        TAiModelConfig provider = new TAiModelConfig();
        provider.setAiModelConfigId(providerId);
        provider.setIsLeaf(false);
        provider.setSort(sort);
        return provider;
    }
}
