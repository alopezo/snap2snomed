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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.snomed.snap2snomed.controller.dto.InheritanceDto;
import org.snomed.snap2snomed.model.MapView;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.springframework.beans.BeanUtils;

/**
 * Applies inheritance to the rows of an export: a row without targets gets one line per inherited
 * target, and every line says where its mapping comes from.
 */
public final class InheritanceExport {

  private InheritanceExport() {
  }

  /**
   * @param origin ASSERTED, INHERITED, NO_MAP, STOPPED, BLOCKED, CONFLICT or UNCOVERED; null when the export
   *     has no inheritance
   * @param inheritedFrom INHERITED: the ancestor's code; BLOCKED: the stopping row; CONFLICT: the
   *     disagreeing ancestors, space separated
   */
  public record Line(MapView view, String origin, String inheritedFrom) {
  }

  /** Lines without inheritance: one per row, with no origin. */
  public static List<Line> plain(List<MapView> views) {
    return views.stream().map(view -> new Line(view, null, null)).toList();
  }

  public static List<Line> apply(List<MapView> views, InheritanceDto inheritance) {
    final Map<String, InheritanceDto.RowDto> byCode = new HashMap<>();
    inheritance.getRows().forEach(row -> byCode.put(row.getCode(), row));
    final List<Line> lines = new ArrayList<>();
    for (final MapView view : views) {
      if (view.getTargetCode() != null) {
        lines.add(new Line(view, "ASSERTED", null));
        continue;
      }
      if (Boolean.TRUE.equals(view.getNoMap())) {
        lines.add(new Line(view, "NO_MAP", null));
        continue;
      }
      final InheritanceDto.RowDto row = byCode.get(view.getSourceCode());
      if (row == null) {
        lines.add(new Line(view, "UNCOVERED", null));
      } else if ("INHERITED".equals(row.getState())) {
        for (final InheritanceDto.TargetDto target : row.getTargets()) {
          final MapView inherited = MapView.builder().build();
          BeanUtils.copyProperties(view, inherited);
          inherited.setTargetCode(target.getCode());
          inherited.setTargetDisplay(target.getDisplay());
          inherited.setRelationship(target.getRelationship() == null ? null : MappingRelationship.valueOf(target.getRelationship()));
          lines.add(new Line(inherited, "INHERITED", row.getFromCode()));
        }
      } else if ("CONFLICT".equals(row.getState())) {
        lines.add(new Line(view, "CONFLICT", String.join(" ", row.getCandidates())));
      } else {
        lines.add(new Line(view, row.getState(), row.getFromCode()));
      }
    }
    return lines;
  }
}
