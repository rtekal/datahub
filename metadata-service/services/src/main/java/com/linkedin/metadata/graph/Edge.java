package com.linkedin.metadata.graph;

import com.linkedin.common.urn.Urn;
import com.linkedin.metadata.query.filter.SortCriterion;
import com.linkedin.metadata.query.filter.SortOrder;
import com.linkedin.metadata.utils.SearchUtil;
import com.linkedin.util.Pair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor
@Slf4j
public class Edge {
  @EqualsAndHashCode.Include private Urn source;
  @EqualsAndHashCode.Include private Urn destination;
  @EqualsAndHashCode.Include private String relationshipType;
  @EqualsAndHashCode.Exclude private Long createdOn;
  @EqualsAndHashCode.Exclude private Urn createdActor;
  @EqualsAndHashCode.Exclude private Long updatedOn;
  @EqualsAndHashCode.Exclude private Urn updatedActor;
  @EqualsAndHashCode.Exclude private Map<String, Object> properties;
  // The entity who owns the lifecycle of this edge
  @EqualsAndHashCode.Include private Urn lifecycleOwner;
  // An entity through which the edge between source and destination is created
  @EqualsAndHashCode.Include private Urn via;

  // For backwards compatibility
  public Edge(
      Urn source,
      Urn destination,
      String relationshipType,
      Long createdOn,
      Urn createdActor,
      Long updatedOn,
      Urn updatedActor,
      Map<String, Object> properties) {
    this(
        source,
        destination,
        relationshipType,
        createdOn,
        createdActor,
        updatedOn,
        updatedActor,
        properties,
        null,
        null);
  }

  public String toDocId() {
    StringBuilder rawDocId = new StringBuilder();
    rawDocId
        .append(getSource().toString())
        .append(DOC_DELIMETER)
        .append(getRelationshipType())
        .append(DOC_DELIMETER)
        .append(getDestination().toString());
    if (getLifecycleOwner() != null && StringUtils.isNotBlank(getLifecycleOwner().toString())) {
      rawDocId.append(DOC_DELIMETER).append(getLifecycleOwner().toString());
    }

    try {
      byte[] bytesOfRawDocID = rawDocId.toString().getBytes(StandardCharsets.UTF_8);
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] thedigest = md.digest(bytesOfRawDocID);
      return Base64.getEncoder().encodeToString(thedigest);
    } catch (NoSuchAlgorithmException e) {
      log.error("Unable to hash document ID, returning unhashed id: " + rawDocId);
      return rawDocId.toString();
    }
  }

  public static final String SOURCE_URN_FIELD = "source.urn";
  public static final String DESTINATION_URN_FIELD = "destination.urn";
  public static final String RELATIONSHIP_TYPE_FIELD = "relationshipType";
  public static final String LIFE_CYCLE_OWNER_FIELD = "lifeCycleOwner";

  public static final List<Pair<String, SortOrder>> KEY_SORTS =
      List.of(
          new Pair<>(SOURCE_URN_FIELD, SortOrder.ASCENDING),
          new Pair<>(DESTINATION_URN_FIELD, SortOrder.ASCENDING),
          new Pair<>(RELATIONSHIP_TYPE_FIELD, SortOrder.ASCENDING),
          new Pair<>(LIFE_CYCLE_OWNER_FIELD, SortOrder.ASCENDING));
  public static List<SortCriterion> EDGE_SORT_CRITERION =
      KEY_SORTS.stream()
          .map(entry -> SearchUtil.sortBy(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());
  private static final String DOC_DELIMETER = "--";
}
