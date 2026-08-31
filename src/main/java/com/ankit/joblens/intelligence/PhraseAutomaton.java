package com.ankit.joblens.intelligence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

public final class PhraseAutomaton<T> {
  private final Node<T> root = new Node<>();

  public PhraseAutomaton(List<Entry<T>> entries) {
    entries.forEach(this::insert);
    buildFailureLinks();
  }

  public List<Hit<T>> find(String text) {
    String folded = text.toLowerCase(Locale.ROOT);
    var hits = new ArrayList<Hit<T>>();
    Node<T> state = root;
    for (int index = 0; index < folded.length(); index++) {
      char character = folded.charAt(index);
      while (state != root && !state.children.containsKey(character)) {
        state = state.failure;
      }
      state = state.children.getOrDefault(character, root);
      for (IndexedEntry<T> output : state.outputs) {
        int start = index - output.normalizedPhrase().length() + 1;
        int end = index + 1;
        if (start >= 0 && boundary(text, start, end)) {
          hits.add(
              new Hit<>(output.value(), output.phrase(), text.substring(start, end), start, end));
        }
      }
    }
    return List.copyOf(hits);
  }

  private void insert(Entry<T> entry) {
    String normalized = entry.phrase().trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return;
    }
    Node<T> node = root;
    for (char character : normalized.toCharArray()) {
      node = node.children.computeIfAbsent(character, ignored -> new Node<>());
    }
    node.outputs.add(new IndexedEntry<>(entry.value(), entry.phrase().trim(), normalized));
  }

  private void buildFailureLinks() {
    Queue<Node<T>> queue = new ArrayDeque<>();
    root.failure = root;
    root.children
        .values()
        .forEach(
            child -> {
              child.failure = root;
              queue.add(child);
            });
    while (!queue.isEmpty()) {
      Node<T> current = queue.remove();
      for (Map.Entry<Character, Node<T>> edge : current.children.entrySet()) {
        char character = edge.getKey();
        Node<T> child = edge.getValue();
        Node<T> fallback = current.failure;
        while (fallback != root && !fallback.children.containsKey(character)) {
          fallback = fallback.failure;
        }
        if (fallback.children.containsKey(character) && fallback.children.get(character) != child) {
          fallback = fallback.children.get(character);
        }
        child.failure = fallback;
        child.outputs.addAll(fallback.outputs);
        queue.add(child);
      }
    }
  }

  private static boolean boundary(String text, int start, int end) {
    return (start == 0 || !termCharacter(text.charAt(start - 1)))
        && (end == text.length() || !termCharacter(text.charAt(end)));
  }

  private static boolean termCharacter(char character) {
    return Character.isLetterOrDigit(character) || character == '+' || character == '#';
  }

  public record Entry<T>(T value, String phrase) {}

  public record Hit<T>(T value, String phrase, String surface, int start, int end) {}

  private static final class Node<T> {
    private final Map<Character, Node<T>> children = new HashMap<>();
    private final List<IndexedEntry<T>> outputs = new ArrayList<>();
    private Node<T> failure;
  }

  private record IndexedEntry<T>(T value, String phrase, String normalizedPhrase) {}
}
