package com.ankit.joblens.intelligence.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// Model/tokenizer are fetched by the Maven build (download-maven-plugin in pom.xml) onto the
// classpath, same convention as the frontend build's own generated assets; no runtime network call.
@Component
@ConditionalOnProperty(
    prefix = "joblens.onboarding.semantic-matching",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OnnxTextEmbeddingModel implements TextEmbeddingModel {
  private static final String MODEL_CLASSPATH_DIR = "models/all-MiniLM-L6-v2/";
  // DJL's OrtModel falls back to a file literally named "model.onnx" when no explicit model name is
  // set, so the quantized export is renamed on extraction rather than kept as model_quantized.onnx.
  private static final Map<String, String> MODEL_FILES =
      Map.of(
          "model_quantized.onnx", "model.onnx",
          "tokenizer.json", "tokenizer.json",
          "config.json", "config.json");

  private final ZooModel<String, float[]> model;
  private final Predictor<String, float[]> predictor;

  public OnnxTextEmbeddingModel(
      @Value("${joblens.onboarding.semantic-matching.model-directory:}") String overrideDirectory) {
    try {
      Path modelDirectory =
          overrideDirectory == null || overrideDirectory.isBlank()
              ? extractBundledModel()
              : Path.of(overrideDirectory);
      Criteria<String, float[]> criteria =
          Criteria.builder()
              .setTypes(String.class, float[].class)
              .optModelPath(modelDirectory)
              .optEngine("OnnxRuntime")
              .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
              // This ONNX export requires token_type_ids as a third model input.
              .optArgument("includeTokenTypes", true)
              .build();
      this.model = criteria.loadModel();
      this.predictor = model.newPredictor();
    } catch (IOException | ModelNotFoundException | MalformedModelException e) {
      throw new IllegalStateException("Failed to load the bundled sentence-embedding model", e);
    }
  }

  @Override
  public float[] embed(String text) {
    try {
      return predictor.predict(text);
    } catch (TranslateException e) {
      throw new IllegalStateException("Failed to embed text with the local sentence model", e);
    }
  }

  @PreDestroy
  void close() {
    predictor.close();
    model.close();
  }

  private static Path extractBundledModel() {
    try {
      Path directory = Files.createTempDirectory("joblens-embedding-model");
      directory.toFile().deleteOnExit();
      for (Map.Entry<String, String> file : MODEL_FILES.entrySet()) {
        Path target = directory.resolve(file.getValue());
        target.toFile().deleteOnExit();
        try (InputStream in =
            new ClassPathResource(MODEL_CLASSPATH_DIR + file.getKey()).getInputStream()) {
          Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      return directory;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Bundled sentence-embedding model files are missing from the classpath; the Maven build "
              + "must download them (see the download-maven-plugin binding in pom.xml) before packaging",
          e);
    }
  }
}
