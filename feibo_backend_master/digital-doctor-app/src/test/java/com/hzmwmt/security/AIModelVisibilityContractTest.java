package com.hzmwmt.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.api.dto.AIModelConfigIndexResponseDTO;
import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.infrastructure.adapter.repository.AIRepository;
import com.hzmwmt.infrastructure.dao.AIModelConfigMapper;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIModelVisibilityContractTest {

    private AIModelConfigMapper mapper;
    private AIRepository repository;

    @BeforeEach
    void setUp() {
        mapper = mock(AIModelConfigMapper.class);
        repository = new AIRepository();
        ReflectionTestUtils.setField(repository, "aiModelConfigMapper", mapper);
    }

    @Test
    void emptyCategoryAndRootArePrunedAfterTheirLastLeafIsHidden() {
        when(mapper.selectAllAiModelConfig()).thenReturn(List.of(root(), category()));

        assertThat(repository.queryAiModelConfigIndex()).isEmpty();
    }

    @Test
    void categoryIsKeptWhileItStillHasAVisibleLeaf() {
        when(mapper.selectAllAiModelConfig())
                .thenReturn(List.of(root(), category(), leaf()));

        List<AIModelConfigAggregate> tree = repository.queryAiModelConfigIndex();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildrenEntityList()).hasSize(1);
        assertThat(tree.get(0).getChildrenEntityList().get(0).getChildrenEntityList())
                .hasSize(1);
    }

    @Test
    void providerVersionsStayUnderTheirPhysicalProviderAndUseStableLeafOrder() {
        AIModelConfig kimi = category(2L, 9005300000000000000L, "Kimi", 2);
        AIModelConfig qwen = category(6L, 9005400000000000000L, "Qwen", 1);
        AIModelConfig kimiNew = leaf(
                3L, 9005300000000000002L, 2L, "kimik3", "Kimi New", null);
        AIModelConfig kimiOld = leaf(
                5L, 9005300000000000001L, 2L, "kimik3", "Kimi Old", null);
        AIModelConfig qwenLeaf = leaf(
                7L, 9005400000000000001L, 6L, "qwen", "Qwen", 1);
        when(mapper.selectAllAiModelConfig()).thenReturn(List.of(
                kimiNew, qwenLeaf, root(), kimiOld, qwen, kimi));

        AIModelConfigAggregate rootAggregate = repository.queryAiModelConfigIndex().get(0);

        assertThat(rootAggregate.getChildrenEntityList())
                .extracting(child -> child.getData().getName())
                .containsExactly("Qwen", "Kimi");
        AIModelConfigAggregate kimiAggregate = rootAggregate.getChildrenEntityList().get(1);
        assertThat(kimiAggregate.getChildrenEntityList())
                .extracting(child -> child.getData().getName())
                .containsExactly("Kimi Old", "Kimi New");
        assertThat(kimiAggregate.getChildrenEntityList())
                .extracting(child -> child.getData().getAiModelConfigId())
                .containsExactly(9005300000000000001L, 9005300000000000002L);
    }

    @Test
    void modelCodeNeverMergesDistinctPhysicalProviderCategories() {
        AIModelConfig firstProvider = category(2L, 9005300000000000000L, "First", 1);
        AIModelConfig secondProvider = category(4L, 9005400000000000000L, "Second", 2);
        AIModelConfig firstLeaf = leaf(
                3L, 9005300000000000001L, 2L, "shared", "First v1", 10);
        AIModelConfig secondLeaf = leaf(
                5L, 9005400000000000001L, 4L, "shared", "Second v1", 10);
        when(mapper.selectAllAiModelConfig()).thenReturn(List.of(
                secondLeaf, firstProvider, root(), firstLeaf, secondProvider));

        AIModelConfigAggregate rootAggregate = repository.queryAiModelConfigIndex().get(0);

        assertThat(rootAggregate.getChildrenEntityList())
                .extracting(child -> child.getData().getName())
                .containsExactly("First", "Second");
    }

    @Test
    void publicModelBusinessIdSerializesAsAString() throws Exception {
        AIModelConfigIndexResponseDTO response = AIModelConfigIndexResponseDTO.builder()
                .aiModelConfigId(9005300000000000001L)
                .name("Kimi K3")
                .build();

        JsonNode json = new ObjectMapper().readTree(
                new ObjectMapper().writeValueAsString(response));

        assertThat(json.get("aiModelConfigId").isTextual()).isTrue();
        assertThat(json.get("aiModelConfigId").textValue())
                .isEqualTo("9005300000000000001");
    }

    @Test
    void hiddenModelIdCannotResolveForDirectChat() {
        when(mapper.selectAiModelLeafConfigByAiModelConfigId(3L)).thenReturn(null);

        assertThat(repository.queryAiModelLeafConfig(3L)).isNull();
    }

    @Test
    void mapperFiltersBothThePublicTreeAndDirectLeafLookup() throws Exception {
        String mapperXml = Files.readString(Path.of(
                "src/main/resources/mybatis/mapper/AIModelConfigMapper.xml"));

        assertThat(mapperXml).contains("from t_ai_model_config\n        where visible = true");
        assertThat(mapperXml).contains("and is_leaf = true\n          and visible = true");
        assertThat(mapperXml).contains("and is_deleted = false");
        assertThat(mapperXml).contains("display_description");
        assertThat(mapperXml).contains(
                "order by parent_id asc, (sort IS NULL) asc, sort asc,\n" +
                        "                 ai_model_config_id asc, id asc");
    }

    @Test
    void publicTreeUsesDisplayDescriptionWithoutChangingLegacyRuntimeDescription() {
        AIModelConfig leaf = leaf();
        leaf.setDescription("legacy-system-prompt");
        leaf.setDisplayDescription("Public introduction");
        when(mapper.selectAllAiModelConfig())
                .thenReturn(List.of(root(), category(), leaf));

        AIModelConfigAggregate leafAggregate = repository.queryAiModelConfigIndex()
                .get(0).getChildrenEntityList().get(0).getChildrenEntityList().get(0);

        assertThat(leafAggregate.getData().getDescription())
                .isEqualTo("Public introduction");
        assertThat(leaf.getDescription()).isEqualTo("legacy-system-prompt");
    }

    private AIModelConfig root() {
        return AIModelConfig.builder()
                .id(1L)
                .aiModelConfigId(1970339278848659457L)
                .parentId(0L)
                .isLeaf(false)
                .name("通用模型")
                .visible(true)
                .isDeleted(false)
                .build();
    }

    private AIModelConfig category() {
        return AIModelConfig.builder()
                .id(2L)
                .aiModelConfigId(9005300000000000000L)
                .parentId(1L)
                .isLeaf(false)
                .name("Kimi")
                .visible(true)
                .isDeleted(false)
                .build();
    }

    private AIModelConfig leaf() {
        return AIModelConfig.builder()
                .id(3L)
                .aiModelConfigId(9005300000000000001L)
                .parentId(2L)
                .isLeaf(true)
                .name("kimi-k3")
                .displayName("Kimi K3")
                .modelCode("kimik3")
                .visible(true)
                .isDeleted(false)
                .build();
    }

    private AIModelConfig category(
            long id,
            long businessId,
            String name,
            int sort) {
        return AIModelConfig.builder()
                .id(id)
                .aiModelConfigId(businessId)
                .parentId(1L)
                .isLeaf(false)
                .name(name)
                .sort(sort)
                .visible(true)
                .isDeleted(false)
                .build();
    }

    private AIModelConfig leaf(
            long id,
            long businessId,
            long parentId,
            String modelCode,
            String displayName,
            Integer sort) {
        return AIModelConfig.builder()
                .id(id)
                .aiModelConfigId(businessId)
                .parentId(parentId)
                .isLeaf(true)
                .name(displayName.toLowerCase().replace(' ', '-'))
                .displayName(displayName)
                .modelCode(modelCode)
                .sort(sort)
                .visible(true)
                .isDeleted(false)
                .build();
    }
}
