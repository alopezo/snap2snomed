/*
 * Copyright © 2026 SNOMED International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.snomed.snap2snomed.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the code of a (small) code set whose display best matches a term: tokens compared after
 * normalising case, accents and punctuation, weighted by how rare they are in the code set, with typos and
 * variants tolerated through character trigrams. The score is the harmonic mean of how much of the term the
 * display covers and how much of the display the term covers (0..1); below the threshold there is no match.
 *
 * <p>Built once per code set and reused for every term: candidates come from an inverted index of tokens.
 */
public final class TermMatcher {

  /** Lowest score accepted as a match by default. */
  public static final double DEFAULT_THRESHOLD = 0.75;

  /** Lowest trigram similarity for two different tokens to count as the same (typos, plurals). */
  static final double TOKEN_SIMILARITY = 0.6;

  private static final Pattern SEMANTIC_TAG = Pattern.compile("\\s*\\([^()]*\\)\\s*$");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{Alnum}]+");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_PREFIX = Pattern.compile("\\bnon[\\s-]+(?=\\p{Alpha})");
  private static final Set<String> STOPWORDS = Set.of("of", "the", "and", "in", "on", "a", "an", "to", "by", "for",
      "de", "del", "la", "las", "el", "los", "y", "en", "por", "le", "les", "des", "du", "et", "het", "van", "og",
      "af", "och", "av", "es", "az");

  public record Candidate(String code, String display) {
  }

  public record Match(String code, String display, double score) {
  }

  /**
   * Words that negate (in the UI languages, accents removed): they are not scored, but a term and a display
   * must agree on them, or "asthma without status asthmaticus" would match "status asthmaticus".
   */
  private static final Set<String> NEGATIONS = Set.of("no", "not", "without", "non", "sin", "sans", "pas", "uden",
      "ikke", "zonder", "niet", "geen", "utan", "inte", "nelkul", "nem");

  /** Prefixes that negate a word: "nonallergic" is never a variant of "allergic". */
  private static final List<String> NEGATING_PREFIXES = List.of("non", "un", "in", "dis", "no", "a");

  private record Entry(Candidate candidate, List<String> tokens, Set<String> negations) {
  }

  private final List<Entry> entries = new ArrayList<>();
  private final Map<String, List<Integer>> entriesByToken = new HashMap<>();
  private final Map<String, Double> idf = new HashMap<>();
  private final Map<String, Set<String>> tokensByTrigram = new HashMap<>();
  private final Map<String, Map<String, Double>> similarCache = new HashMap<>();
  private final double unknownIdf;

  public TermMatcher(List<Candidate> candidates) {
    final Map<String, Integer> documentFrequency = new HashMap<>();
    for (final Candidate candidate : candidates) {
      final List<String> words = tokens(candidate.display());
      final List<String> tokens = withoutNegations(words);
      if (tokens.isEmpty()) {
        continue;
      }
      final int index = entries.size();
      entries.add(new Entry(candidate, tokens, negations(words)));
      for (final String token : new LinkedHashSet<>(tokens)) {
        entriesByToken.computeIfAbsent(token, t -> new ArrayList<>()).add(index);
        documentFrequency.merge(token, 1, Integer::sum);
      }
    }
    final int total = Math.max(entries.size(), 1);
    documentFrequency.forEach((token, df) -> idf.put(token, Math.log(1.0 + (double) total / df)));
    unknownIdf = Math.log(1.0 + total);
    for (final String token : documentFrequency.keySet()) {
      for (final String trigram : trigrams(token)) {
        tokensByTrigram.computeIfAbsent(trigram, t -> new HashSet<>()).add(token);
      }
    }
  }

  /** The best match of the term over the threshold, if any (ties go to the shorter display). */
  public Optional<Match> best(String term, double threshold) {
    final List<String> words = tokens(stripSemanticTag(term));
    final List<String> query = withoutNegations(words);
    final Set<String> queryNegations = negations(words);
    if (query.isEmpty()) {
      return Optional.empty();
    }
    // the similar code set tokens of each query token, and the entries that have any of them
    final Map<String, Map<String, Double>> similar = new HashMap<>();
    final Set<Integer> candidates = new HashSet<>();
    for (final String token : query) {
      final Map<String, Double> tokens = similar.computeIfAbsent(token, this::similarTokens);
      tokens.keySet().forEach(t -> candidates.addAll(entriesByToken.getOrDefault(t, List.of())));
    }
    return candidates.stream()
        .filter(i -> entries.get(i).negations().equals(queryNegations))
        .map(i -> score(query, similar, entries.get(i)))
        .filter(m -> m.score() >= threshold)
        .max(Comparator.comparingDouble(Match::score)
            .thenComparing(m -> -m.display().length()));
  }

  private Match score(List<String> query, Map<String, Map<String, Double>> similar, Entry entry) {
    final Set<String> queryTokens = new LinkedHashSet<>(query);
    final Set<String> entryTokens = new LinkedHashSet<>(entry.tokens());
    // how much of the term the display covers
    double covered = 0;
    double weight = 0;
    for (final String token : queryTokens) {
      final double w = idf.getOrDefault(token, unknownIdf);
      weight += w;
      covered += w * entryTokens.stream().mapToDouble(t -> similar.get(token).getOrDefault(t, 0.0)).max().orElse(0);
    }
    final double recall = weight == 0 ? 0 : covered / weight;
    // how much of the display the term covers
    double displayCovered = 0;
    double displayWeight = 0;
    for (final String token : entryTokens) {
      final double w = idf.getOrDefault(token, unknownIdf);
      displayWeight += w;
      displayCovered += w * queryTokens.stream().mapToDouble(q -> similar.get(q).getOrDefault(token, 0.0)).max().orElse(0);
    }
    final double precision = displayWeight == 0 ? 0 : displayCovered / displayWeight;
    final double score = recall + precision == 0 ? 0 : 2 * recall * precision / (recall + precision);
    return new Match(entry.candidate().code(), entry.candidate().display(), score);
  }

  /** The code set tokens similar to a token, with their similarity (1 for the token itself). */
  private Map<String, Double> similarTokens(String token) {
    return similarCache.computeIfAbsent(token, t -> {
      final Map<String, Double> result = new HashMap<>();
      if (entriesByToken.containsKey(t)) {
        result.put(t, 1.0);
      }
      // short tokens (abbreviations, numbers) must match exactly
      if (t.length() >= 4) {
        final Set<String> trigrams = trigrams(t);
        final Set<String> others = new HashSet<>();
        trigrams.forEach(g -> others.addAll(tokensByTrigram.getOrDefault(g, Set.of())));
        for (final String other : others) {
          if (!other.equals(t) && other.length() >= 4 && !negatedForm(t, other)) {
            final double similarity = dice(trigrams, trigrams(other));
            if (similarity >= TOKEN_SIMILARITY) {
              result.put(other, similarity);
            }
          }
        }
      }
      return result;
    });
  }

  /** One token is the other with a negating prefix ("allergic", "nonallergic"). */
  static boolean negatedForm(String a, String b) {
    final String shorter = a.length() <= b.length() ? a : b;
    final String longer = a.length() <= b.length() ? b : a;
    return longer.endsWith(shorter)
        && NEGATING_PREFIXES.contains(longer.substring(0, longer.length() - shorter.length()));
  }

  private static List<String> withoutNegations(List<String> words) {
    return words.stream().filter(w -> !NEGATIONS.contains(w)).toList();
  }

  private static Set<String> negations(List<String> words) {
    return words.stream().filter(NEGATIONS::contains).collect(java.util.stream.Collectors.toSet());
  }

  static String stripSemanticTag(String term) {
    return term == null ? "" : SEMANTIC_TAG.matcher(term).replaceFirst("");
  }

  static List<String> tokens(String text) {
    if (text == null) {
      return List.of();
    }
    // "non-allergic" and "non allergic" are written as one word, like "nonallergic"
    final String normalized = NON_PREFIX.matcher(
        DIACRITICS.matcher(Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)).replaceAll("")).replaceAll("non");
    return Arrays.stream(NON_ALPHANUMERIC.split(normalized))
        .filter(t -> !t.isEmpty() && !STOPWORDS.contains(t))
        .toList();
  }

  static Set<String> trigrams(String token) {
    final String padded = "  " + token + " ";
    final Set<String> result = new HashSet<>();
    for (int i = 0; i + 3 <= padded.length(); i++) {
      result.add(padded.substring(i, i + 3));
    }
    return result;
  }

  static double dice(Set<String> a, Set<String> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return 0;
    }
    int common = 0;
    for (final String g : a) {
      if (b.contains(g)) {
        common++;
      }
    }
    return 2.0 * common / (a.size() + b.size());
  }
}
