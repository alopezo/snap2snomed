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

package org.snomed.snap2snomed.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.snomed.snap2snomed.controller.dto.TargetCodeDto;
import org.snomed.snap2snomed.model.AdditionalCodeColumn;
import org.snomed.snap2snomed.model.ImportedCode;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.problem.auth.NoSuchUserProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.repository.ImportedCodeRepository;
import org.snomed.snap2snomed.repository.MapRepository;
import org.snomed.snap2snomed.security.WebSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Searches the target code set of a map from SNOMED CT. Access is granted through the map (any role in
 * its project), so the generic code set permissions are left unchanged.
 */
@RestController
public class TargetCodeRestController {

  static final int MAX_PAGE_SIZE = 100;

  @Autowired
  WebSecurity webSecurity;

  @Autowired
  MapRepository mapRepository;

  @Autowired
  ImportedCodeRepository importedCodeRepository;

  @Operation(description = "Searches the target code set of a map from SNOMED CT by code or display text, or "
      + "returns the code with exactly the given value.")
  @GetMapping(path = "/map/{mapId}/targetCodes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<TargetCodeDto> searchTargetCodes(@PathVariable("mapId") Long mapId,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) String code,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "50") int size) {
    if (!webSecurity.isValidUser()) {
      throw new NoSuchUserProblem();
    }
    if (!webSecurity.isAdminUser() && !webSecurity.hasAnyProjectRoleForMapId(mapId)) {
      throw new NotAuthorisedProblem("Not authorised to view the target codes of this map");
    }
    final Map map = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    if (!map.isFromSnomed() || map.getTargetCodeSet() == null) {
      throw new BadRequestDataProblem("Only maps from SNOMED CT have a target code set");
    }
    final Long codeSetId = map.getTargetCodeSet().getId();
    final List<AdditionalCodeColumn> columns = map.getTargetCodeSet().getAdditionalColumnsMetadata();

    final List<ImportedCode> codes;
    if (code != null && !code.isBlank()) {
      codes = importedCodeRepository.findInCodeSetByCode(codeSetId, code.trim());
    } else {
      final String pattern = "%" + (text == null ? "" : text.trim().toLowerCase(Locale.ROOT)
          .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
      final Page<ImportedCode> result = importedCodeRepository.searchInCodeSet(codeSetId, pattern,
          PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
      codes = result.getContent();
    }
    return codes.stream().map(c -> toDto(c, columns)).toList();
  }

  static TargetCodeDto toDto(ImportedCode code, List<AdditionalCodeColumn> columns) {
    final List<TargetCodeDto.Column> additional = new ArrayList<>();
    if (code.getAdditionalColumns() != null && columns != null) {
      for (int i = 0; i < code.getAdditionalColumns().size() && i < columns.size(); i++) {
        additional.add(new TargetCodeDto.Column(columns.get(i).getName(), code.getAdditionalColumns().get(i).getValue()));
      }
    }
    return new TargetCodeDto(code.getCode(), code.getDisplay(), additional);
  }
}
