package com.ankit.joblens.discovery;

import java.util.Optional;

public interface SourceBoardDetector {
  Optional<DetectedSourceBoard> detect(String sourceUrl);
}
