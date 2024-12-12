package io.datahubproject.openapi.v3.controller;

import static com.linkedin.metadata.Constants.DATASET_ENTITY_NAME;
import static com.linkedin.metadata.Constants.DATASET_PROFILE_ASPECT_NAME;
import static com.linkedin.metadata.Constants.STRUCTURED_PROPERTY_DEFINITION_ASPECT_NAME;
import static com.linkedin.metadata.Constants.STRUCTURED_PROPERTY_ENTITY_NAME;
import static com.linkedin.metadata.utils.GenericRecordUtils.JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;

import com.datahub.authentication.Actor;
import com.datahub.authentication.ActorType;
import com.datahub.authentication.Authentication;
import com.datahub.authentication.AuthenticationContext;
import com.datahub.authorization.AuthorizationResult;
import com.datahub.authorization.AuthorizerChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.common.Status;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.dataset.DatasetProfile;
import com.linkedin.entity.Aspect;
import com.linkedin.entity.EnvelopedAspect;
import com.linkedin.metadata.aspect.batch.AspectsBatch;
import com.linkedin.metadata.entity.EntityService;
import com.linkedin.metadata.entity.EntityServiceImpl;
import com.linkedin.metadata.graph.elastic.ElasticSearchGraphService;
import com.linkedin.metadata.models.AspectSpec;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.query.filter.SortOrder;
import com.linkedin.metadata.search.ScrollResult;
import com.linkedin.metadata.search.SearchEntity;
import com.linkedin.metadata.search.SearchEntityArray;
import com.linkedin.metadata.search.SearchService;
import com.linkedin.metadata.timeseries.TimeseriesAspectService;
import com.linkedin.metadata.utils.GenericRecordUtils;
import com.linkedin.metadata.utils.SearchUtil;
import com.linkedin.mxe.GenericAspect;
import io.datahubproject.metadata.context.OperationContext;
import io.datahubproject.metadata.context.ValidationContext;
import io.datahubproject.openapi.config.SpringWebConfig;
import io.datahubproject.openapi.exception.InvalidUrnException;
import io.datahubproject.test.metadata.context.TestOperationContexts;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testng.annotations.Test;

@SpringBootTest(classes = {SpringWebConfig.class})
@ComponentScan(basePackages = {"io.datahubproject.openapi.v3.controller"})
@Import({SpringWebConfig.class, EntityControllerTest.EntityControllerTestConfig.class})
@AutoConfigureWebMvc
@AutoConfigureMockMvc
public class EntityControllerTest extends AbstractTestNGSpringContextTests {
  @Autowired private EntityController entityController;
  @Autowired private MockMvc mockMvc;
  @Autowired private SearchService mockSearchService;
  @Autowired private EntityService<?> mockEntityService;
  @Autowired private TimeseriesAspectService mockTimeseriesAspectService;
  @Autowired private EntityRegistry entityRegistry;
  @Autowired private OperationContext opContext;

  @Test
  public void initTest() {
    assertNotNull(entityController);
  }

  @Test
  public void testSearchOrderPreserved() throws Exception {
    List<Urn> TEST_URNS =
        List.of(
            UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:testPlatform,1,PROD)"),
            UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:testPlatform,2,PROD)"),
            UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:testPlatform,3,PROD)"));

    // Mock scroll ascending/descending results
    ScrollResult expectedResultAscending =
        new ScrollResult()
            .setEntities(
                new SearchEntityArray(
                    List.of(
                        new SearchEntity().setEntity(TEST_URNS.get(0)),
                        new SearchEntity().setEntity(TEST_URNS.get(1)),
                        new SearchEntity().setEntity(TEST_URNS.get(2)))));
    when(mockSearchService.scrollAcrossEntities(
            any(OperationContext.class),
            eq(List.of("dataset")),
            anyString(),
            nullable(Filter.class),
            eq(Collections.singletonList(SearchUtil.sortBy("urn", SortOrder.valueOf("ASCENDING")))),
            nullable(String.class),
            nullable(String.class),
            anyInt()))
        .thenReturn(expectedResultAscending);
    ScrollResult expectedResultDescending =
        new ScrollResult()
            .setEntities(
                new SearchEntityArray(
                    List.of(
                        new SearchEntity().setEntity(TEST_URNS.get(2)),
                        new SearchEntity().setEntity(TEST_URNS.get(1)),
                        new SearchEntity().setEntity(TEST_URNS.get(0)))));
    when(mockSearchService.scrollAcrossEntities(
            any(OperationContext.class),
            eq(List.of("dataset")),
            anyString(),
            nullable(Filter.class),
            eq(
                Collections.singletonList(
                    SearchUtil.sortBy("urn", SortOrder.valueOf("DESCENDING")))),
            nullable(String.class),
            nullable(String.class),
            anyInt()))
        .thenReturn(expectedResultDescending);
    // Mock entity aspect
    when(mockEntityService.getEnvelopedVersionedAspects(
            any(OperationContext.class), anyMap(), eq(false)))
        .thenReturn(
            Map.of(
                TEST_URNS.get(0),
                    List.of(
                        new EnvelopedAspect()
                            .setName("status")
                            .setValue(new Aspect(new Status().data()))),
                TEST_URNS.get(1),
                    List.of(
                        new EnvelopedAspect()
                            .setName("status")
                            .setValue(new Aspect(new Status().data()))),
                TEST_URNS.get(2),
                    List.of(
                        new EnvelopedAspect()
                            .setName("status")
                            .setValue(new Aspect(new Status().data())))));

    // test ASCENDING
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/v3/entity/dataset")
                .param("sortOrder", "ASCENDING")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[0].urn").value(TEST_URNS.get(0).toString()))
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[1].urn").value(TEST_URNS.get(1).toString()))
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[2].urn").value(TEST_URNS.get(2).toString()));

    // test DESCENDING
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/v3/entity/dataset")
                .accept(MediaType.APPLICATION_JSON)
                .param("sortOrder", "DESCENDING"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[0].urn").value(TEST_URNS.get(2).toString()))
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[1].urn").value(TEST_URNS.get(1).toString()))
        .andExpect(
            MockMvcResultMatchers.jsonPath("$.entities[2].urn").value(TEST_URNS.get(0).toString()));
  }

  @Test
  public void testDeleteEntity() throws Exception {
    Urn TEST_URN = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:testPlatform,4,PROD)");

    // test delete entity
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(String.format("/v3/entity/dataset/%s", TEST_URN))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // test delete entity by aspect key
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(String.format("/v3/entity/dataset/%s", TEST_URN))
                .param("aspects", "datasetKey")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    verify(mockEntityService, times(2)).deleteUrn(any(), eq(TEST_URN));

    // test delete entity by non-key aspect
    reset(mockEntityService);
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(String.format("/v3/entity/dataset/%s", TEST_URN))
                .param("aspects", "status")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    verify(mockEntityService, times(1))
        .deleteAspect(any(), eq(TEST_URN.toString()), eq("status"), anyMap(), eq(true));

    // test delete entity clear
    reset(mockEntityService);
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(String.format("/v3/entity/dataset/%s", TEST_URN))
                .param("clear", "true")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    entityRegistry.getEntitySpec(DATASET_ENTITY_NAME).getAspectSpecs().stream()
        .map(AspectSpec::getName)
        .filter(aspectName -> !"datasetKey".equals(aspectName))
        .forEach(
            aspectName ->
                verify(mockEntityService)
                    .deleteAspect(
                        any(), eq(TEST_URN.toString()), eq(aspectName), anyMap(), eq(true)));
  }

  @Test
  public void testAlternativeMCPValidation() throws InvalidUrnException, JsonProcessingException {
    final AspectSpec aspectSpec =
        entityRegistry
            .getEntitySpec(STRUCTURED_PROPERTY_ENTITY_NAME)
            .getAspectSpec(STRUCTURED_PROPERTY_DEFINITION_ASPECT_NAME);

    // Enable Alternative MCP Validation via mock
    OperationContext opContextSpy = spy(opContext);
    ValidationContext mockValidationContext = mock(ValidationContext.class);
    when(mockValidationContext.isAlternateValidation()).thenReturn(true);
    when(opContextSpy.getValidationContext()).thenReturn(mockValidationContext);

    final String testBody =
        "[\n"
            + "    {\n"
            + "      \"urn\": \"urn:li:structuredProperty:io.acryl.privacy.retentionTime05\",\n"
            + "      \"propertyDefinition\": {\n"
            + "        \"value\": {\n"
            + "          \"allowedValues\": [\n"
            + "            {\n"
            + "              \"value\": {\n"
            + "                \"string\": \"foo2\"\n"
            + "              },\n"
            + "              \"description\": \"test foo2 value\"\n"
            + "            },\n"
            + "            {\n"
            + "              \"value\": {\n"
            + "                \"string\": \"bar2\"\n"
            + "              },\n"
            + "              \"description\": \"test bar2 value\"\n"
            + "            }\n"
            + "          ],\n"
            + "          \"entityTypes\": [\n"
            + "            \"urn:li:entityType:datahub.dataset\"\n"
            + "          ],\n"
            + "          \"qualifiedName\": \"io.acryl.privacy.retentionTime05\",\n"
            + "          \"displayName\": \"Retention Time 03\",\n"
            + "          \"cardinality\": \"SINGLE\",\n"
            + "          \"valueType\": \"urn:li:dataType:datahub.string\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "]";

    AspectsBatch testAspectsBatch =
        entityController.toMCPBatch(
            opContextSpy,
            testBody,
            opContext.getSessionActorContext().getAuthentication().getActor());

    GenericAspect aspect =
        testAspectsBatch.getMCPItems().get(0).getMetadataChangeProposal().getAspect();
    RecordTemplate propertyDefinition =
        GenericRecordUtils.deserializeAspect(aspect.getValue(), JSON, aspectSpec);
    assertEquals(
        propertyDefinition.data().get("entityTypes"), List.of("urn:li:entityType:datahub.dataset"));

    // test alternative
    reset(mockValidationContext);
    when(mockValidationContext.isAlternateValidation()).thenReturn(false);
    testAspectsBatch =
        entityController.toMCPBatch(
            opContextSpy,
            testBody,
            opContext.getSessionActorContext().getAuthentication().getActor());

    aspect = testAspectsBatch.getMCPItems().get(0).getMetadataChangeProposal().getAspect();
    propertyDefinition = GenericRecordUtils.deserializeAspect(aspect.getValue(), JSON, aspectSpec);
    assertEquals(
        propertyDefinition.data().get("entityTypes"), List.of("urn:li:entityType:datahub.dataset"));
  }

  @Test
  public void testTimeseriesAspect() throws Exception {
    Urn TEST_URN = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:testPlatform,1,PROD)");
    DatasetProfile firstDatasetProfile =
        new DatasetProfile()
            .setRowCount(1)
            .setColumnCount(10)
            .setMessageId("testOld")
            .setTimestampMillis(100);
    DatasetProfile secondDatasetProfile =
        new DatasetProfile()
            .setRowCount(10)
            .setColumnCount(100)
            .setMessageId("testLatest")
            .setTimestampMillis(200);

    // Mock expected timeseries service response
    when(mockTimeseriesAspectService.getLatestTimeseriesAspectValues(
            any(OperationContext.class),
            eq(Set.of(TEST_URN)),
            eq(Set.of(DATASET_PROFILE_ASPECT_NAME)),
            eq(Map.of(DATASET_PROFILE_ASPECT_NAME, 150L))))
        .thenReturn(
            Map.of(
                TEST_URN,
                Map.of(
                    DATASET_PROFILE_ASPECT_NAME,
                    new com.linkedin.metadata.aspect.EnvelopedAspect()
                        .setAspect(GenericRecordUtils.serializeAspect(firstDatasetProfile)))));

    when(mockTimeseriesAspectService.getLatestTimeseriesAspectValues(
            any(OperationContext.class),
            eq(Set.of(TEST_URN)),
            eq(Set.of(DATASET_PROFILE_ASPECT_NAME)),
            eq(Map.of())))
        .thenReturn(
            Map.of(
                TEST_URN,
                Map.of(
                    DATASET_PROFILE_ASPECT_NAME,
                    new com.linkedin.metadata.aspect.EnvelopedAspect()
                        .setAspect(GenericRecordUtils.serializeAspect(secondDatasetProfile)))));

    // test timeseries latest aspect
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/v3/entity/dataset/{urn}/datasetprofile", TEST_URN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.rowCount").value(10))
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.columnCount").value(100))
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.messageId").value("testLatest"));

    // test oldd aspect
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/v3/entity/dataset/{urn}/datasetprofile", TEST_URN)
                .param("version", "150")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.rowCount").value(1))
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.columnCount").value(10))
        .andExpect(MockMvcResultMatchers.jsonPath("$.value.messageId").value("testOld"));
  }

  @TestConfiguration
  public static class EntityControllerTestConfig {
    @MockBean public EntityServiceImpl entityService;
    @MockBean public SearchService searchService;
    @MockBean public TimeseriesAspectService timeseriesAspectService;

    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean(name = "systemOperationContext")
    public OperationContext systemOperationContext() {
      return TestOperationContexts.systemContextNoSearchAuthorization();
    }

    @Bean("entityRegistry")
    @Primary
    public EntityRegistry entityRegistry(
        @Qualifier("systemOperationContext") final OperationContext testOperationContext) {
      return testOperationContext.getEntityRegistry();
    }

    @Bean("graphService")
    @Primary
    public ElasticSearchGraphService graphService() {
      return mock(ElasticSearchGraphService.class);
    }

    @Bean
    public AuthorizerChain authorizerChain() {
      AuthorizerChain authorizerChain = mock(AuthorizerChain.class);

      Authentication authentication = mock(Authentication.class);
      when(authentication.getActor()).thenReturn(new Actor(ActorType.USER, "datahub"));
      when(authorizerChain.authorize(any()))
          .thenReturn(new AuthorizationResult(null, AuthorizationResult.Type.ALLOW, ""));
      AuthenticationContext.setAuthentication(authentication);

      return authorizerChain;
    }

    @Bean
    public TimeseriesAspectService timeseriesAspectService() {
      return timeseriesAspectService;
    }
  }
}
