package com.linkedin.metadata.config;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MetadataChangeProposalConfig {

  ThrottlesConfig throttle;
  SideEffectsConfig sideEffects;

  @Data
  @Accessors(chain = true)
  public static class ThrottlesConfig {
    Integer updateIntervalMs;
    ThrottleConfig versioned;
    ThrottleConfig timeseries;
  }

  @Data
  @Accessors(chain = true)
  public static class ThrottleConfig {
    boolean enabled;
    Integer threshold;
    Integer maxAttempts;
    Integer initialIntervalMs;
    Integer multiplier;
    Integer maxIntervalMs;
  }

  @Data
  @Accessors(chain = true)
  public static class SideEffectsConfig {
    SideEffectConfig schemaField;
  }

  @Data
  @Accessors(chain = true)
  public static class SideEffectConfig {
    boolean enabled;
  }
}
