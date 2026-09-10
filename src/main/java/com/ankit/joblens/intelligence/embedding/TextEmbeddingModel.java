package com.ankit.joblens.intelligence.embedding;

public interface TextEmbeddingModel {
  float[] embed(String text);
}
