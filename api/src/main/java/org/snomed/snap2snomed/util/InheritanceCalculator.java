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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;

/**
 * Inheritance in a map from SNOMED CT: a concept without targets takes the targets of its nearest
 * mapped ancestor among the map's rows (ancestry follows the real SNOMED CT hierarchy, given by
 * {@code descendantsOf}). A row marked "no map" or "stop inheritance" stops it for its subtree. When the
 * nearest ancestors disagree (polyhierarchy), nothing is chosen: the row is a conflict.
 *
 * <p>Pure computation, shared by the coverage shown while authoring and the export.
 */
public final class InheritanceCalculator {

  private InheritanceCalculator() {
  }

  public enum State {
    /** The row has its own targets. */
    ASSERTED,
    /** The row is marked "no map" (which also stops inheritance for its subtree). */
    NO_MAP,
    /** The row has no targets and is marked "stop inheritance". */
    STOPPED,
    /** Takes the targets of its nearest mapped ancestor. */
    INHERITED,
    /** Its nearest anchor is a "no map" or "stop inheritance" row. */
    BLOCKED,
    /** Its nearest mapped ancestors disagree. */
    CONFLICT,
    /** No mapped ancestor among the rows. */
    UNCOVERED
  }

  public record Target(String code, String display, MappingRelationship relationship) {
  }

  public record Row(String code, boolean noMap, boolean stopInheritance, List<Target> targets) {
    boolean asserted() {
      return targets != null && !targets.isEmpty();
    }

    boolean anchor() {
      return asserted() || noMap || stopInheritance;
    }
  }

  /**
   * @param from the ancestor the targets come from (INHERITED) or the blocking row (BLOCKED)
   * @param candidates the disagreeing nearest ancestors (CONFLICT)
   */
  public record Result(String code, State state, String from, List<Target> targets, List<String> candidates) {
  }

  /** The anchor rows (mapped, no map or stopped): only their descendants are needed. */
  public static Set<String> anchorCodes(Collection<Row> rows) {
    return rows.stream().filter(Row::anchor).map(Row::code).collect(Collectors.toCollection(HashSet::new));
  }

  /**
   * @param descendantsOf the proper descendants of an anchor concept (only anchors are asked)
   * @return one result per row, in row order
   */
  public static List<Result> compute(List<Row> rows, Function<String, Set<String>> descendantsOf) {
    final Map<String, Row> anchors = new LinkedHashMap<>();
    rows.stream().filter(Row::anchor).forEach(row -> anchors.put(row.code(), row));
    final Map<String, Set<String>> descendants = new LinkedHashMap<>();
    anchors.keySet().forEach(code -> descendants.put(code, descendantsOf.apply(code)));

    final List<Result> results = new ArrayList<>(rows.size());
    for (final Row row : rows) {
      if (row.asserted()) {
        results.add(new Result(row.code(), State.ASSERTED, null, row.targets(), List.of()));
      } else if (row.noMap()) {
        results.add(new Result(row.code(), State.NO_MAP, null, List.of(), List.of()));
      } else if (row.stopInheritance()) {
        results.add(new Result(row.code(), State.STOPPED, null, List.of(), List.of()));
      } else {
        results.add(inherit(row.code(), anchors, descendants));
      }
    }
    return results;
  }

  /**
   * Inheritance of the concepts that are descendants of an anchor but not rows of the map: generated at
   * export time, never stored. They inherit, are blocked or are in conflict (never uncovered, as they are
   * below an anchor). Sorted by code.
   */
  public static List<Result> computeGenerated(List<Row> rows, Function<String, Set<String>> descendantsOf) {
    final Map<String, Row> anchors = new LinkedHashMap<>();
    rows.stream().filter(Row::anchor).forEach(row -> anchors.put(row.code(), row));
    final Map<String, Set<String>> descendants = new LinkedHashMap<>();
    anchors.keySet().forEach(code -> descendants.put(code, descendantsOf.apply(code)));
    final Set<String> rowCodes = rows.stream().map(Row::code).collect(Collectors.toSet());
    final java.util.TreeSet<String> generated = new java.util.TreeSet<>();
    descendants.values().forEach(generated::addAll);
    generated.removeAll(rowCodes);
    final List<Result> results = new ArrayList<>(generated.size());
    generated.forEach(code -> results.add(inherit(code, anchors, descendants)));
    return results;
  }

  /**
   * What each of the given concepts would inherit if it had no targets of its own (a concept is never its own
   * descendant, so its own targets are ignored): used to tell whether a new target only repeats inheritance.
   */
  public static Map<String, Result> inheritedWithoutOwnTargets(List<Row> rows, Collection<String> codes,
      Function<String, Set<String>> descendantsOf) {
    final Map<String, Row> anchors = new LinkedHashMap<>();
    rows.stream().filter(Row::anchor).forEach(row -> anchors.put(row.code(), row));
    final Map<String, Set<String>> descendants = new LinkedHashMap<>();
    anchors.keySet().forEach(code -> descendants.put(code, descendantsOf.apply(code)));
    final Map<String, Result> results = new LinkedHashMap<>();
    codes.forEach(code -> results.put(code, inherit(code, anchors, descendants)));
    return results;
  }

  private static Result inherit(String code, Map<String, Row> anchors, Map<String, Set<String>> descendants) {
    final List<String> candidates = descendants.entrySet().stream()
        .filter(entry -> entry.getValue().contains(code))
        .map(Map.Entry::getKey)
        .toList();
    // The nearest candidates: no other candidate is below them
    final List<String> nearest = candidates.stream()
        .filter(a -> candidates.stream().noneMatch(b -> !b.equals(a) && descendants.get(a).contains(b)))
        .toList();
    if (nearest.isEmpty()) {
      return new Result(code, State.UNCOVERED, null, List.of(), List.of());
    }
    final List<Row> nearestRows = nearest.stream().map(anchors::get).toList();
    if (nearestRows.stream().noneMatch(Row::asserted)) {
      return new Result(code, State.BLOCKED, nearest.get(0), List.of(), List.of());
    }
    final Row first = nearestRows.get(0);
    final boolean agree = nearestRows.stream().allMatch(r -> r.asserted() && sameTargets(r.targets(), first.targets()));
    if (!agree) {
      return new Result(code, State.CONFLICT, null, List.of(), nearest);
    }
    final List<Target> inherited = first.targets().stream()
        .map(t -> new Target(t.code(), t.display(), TargetExportForm.focusRelationship(t.relationship())))
        .toList();
    return new Result(code, State.INHERITED, first.code(), inherited, List.of());
  }

  private static boolean sameTargets(List<Target> a, List<Target> b) {
    return Objects.equals(a.stream().map(Target::code).collect(Collectors.toSet()),
        b.stream().map(Target::code).collect(Collectors.toSet()));
  }
}
