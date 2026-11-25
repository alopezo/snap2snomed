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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.snomed.snap2snomed.model.MapView;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;

/**
 * How refined targets (close-to-user expressions, see {@link TargetExpression}) are written in a map export.
 */
public enum TargetExportForm {
  /** The expression as stored. */
  EXPRESSION,
  /** Only the focus concept, with the relationship adjusted because the focus is broader than the refined target. */
  FOCUS,
  /** The expression, plus the focus concept in extra columns (tabular formats only). */
  BOTH;

  /** Reads the request parameter; anything missing or unknown means {@link #EXPRESSION}. */
  public static TargetExportForm of(String value) {
    if (value != null) {
      for (final TargetExportForm form : values()) {
        if (form.name().equalsIgnoreCase(value.trim())) {
          return form;
        }
      }
    }
    return EXPRESSION;
  }

  /**
   * Applies this form to the rows of an export. Only {@link #FOCUS} changes them: each refined target is
   * replaced by its focus concept, and targets of the same source row that end up with the same code are
   * written once.
   */
  public List<MapView> apply(List<MapView> rows) {
    if (this != FOCUS) {
      return rows;
    }
    // Targets grouped by source row and (focus) code, in the order they first appear; rows without a
    // target (no map) get a group of their own
    final Map<Object, List<MapView>> groups = new LinkedHashMap<>();
    final Map<MapView, Boolean> refined = new IdentityHashMap<>();
    for (final MapView row : rows) {
      final Optional<TargetExpression> expression = TargetExpression.parse(row.getTargetCode());
      refined.put(row, expression.isPresent());
      expression.ifPresent(e -> {
        row.setTargetDisplay(focusDisplay(row.getTargetDisplay(), e));
        row.setTargetCode(e.getFocus());
        row.setRelationship(focusRelationship(row.getRelationship()));
      });
      final Object key = row.getTargetCode() == null ? new Object() : row.getSourceIndex() + "|" + row.getTargetCode();
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }
    final List<MapView> result = new ArrayList<>();
    for (final List<MapView> group : groups.values()) {
      // A target that already was the focus concept keeps its own relationship
      final Optional<MapView> plain = group.stream().filter(row -> !refined.get(row)).findFirst();
      if (plain.isPresent()) {
        result.add(plain.get());
        continue;
      }
      final MapView first = group.get(0);
      if (group.stream().anyMatch(row -> row.getRelationship() != first.getRelationship())) {
        first.setRelationship(MappingRelationship.TARGET_INEXACT);
      }
      result.add(first);
    }
    return result;
  }

  /**
   * The relationship of the focus concept, which is broader than the refined target: an equivalent
   * target becomes broader, a narrower one becomes inexact, the others keep theirs.
   */
  public static MappingRelationship focusRelationship(MappingRelationship relationship) {
    if (relationship == null) {
      return null;
    }
    switch (relationship) {
      case TARGET_EQUIVALENT:
        return MappingRelationship.TARGET_BROADER;
      case TARGET_NARROWER:
        return MappingRelationship.TARGET_INEXACT;
      default:
        return relationship;
    }
  }

  /** The focus concept of a target code: the code itself unless it is a refined target. */
  public static String focusCode(String targetCode) {
    return TargetExpression.focusOf(targetCode);
  }

  /** The focus term of a target display, removing the value terms a refined target appends to it. */
  public static String focusDisplay(String targetCode, String targetDisplay) {
    return TargetExpression.parse(targetCode)
        .map(expression -> focusDisplay(targetDisplay, expression))
        .orElse(targetDisplay);
  }

  private static String focusDisplay(String targetDisplay, TargetExpression expression) {
    String display = targetDisplay == null ? "" : targetDisplay;
    for (int i = 0; i < expression.getValues().size(); i++) {
      final int separator = display.lastIndexOf(TargetExpression.DISPLAY_SEPARATOR);
      if (separator < 0) {
        break;
      }
      display = display.substring(0, separator);
    }
    return display;
  }
}
