/*
 * Copyright © 2022 SNOMED International
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

import jakarta.validation.Valid;
import org.snomed.snap2snomed.controller.dto.EclCodeSetRequest;
import org.snomed.snap2snomed.controller.dto.ImportDetails;
import org.snomed.snap2snomed.model.ImportedCodeSet;
import org.snomed.snap2snomed.problem.auth.NoSuchUserProblem;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.service.CodeSetImportService;
import org.snomed.snap2snomed.service.EclCodeSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;

@Validated
@RestController
public class ImportedCodeSetRestController {

  @Autowired
  private CodeSetImportService importService;

  @Autowired
  WebSecurity webSecurity;

  @Autowired
  private EclCodeSetService eclCodeSetService;  

  @Operation(description = "Creates the source code set of a map from SNOMED CT: the active concepts of an ECL "
      + "expression (at most " + EclCodeSetService.MAX_CONCEPTS + "), with their terms in the given display language.")
  @PostMapping(value = "/importedCodeSets/fromEcl", consumes = {MediaType.APPLICATION_JSON_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE})
  ImportedCodeSet createCodeSetFromEcl(@RequestBody @Valid EclCodeSetRequest request) {
    if (!webSecurity.isValidUser()) {
      throw new NoSuchUserProblem();
    }
    return eclCodeSetService.createCodeSet(request);
  }

  @Operation
  @PostMapping(value = "/importedCodeSets/import", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE})
  ImportedCodeSet importCodeSet(
      @Parameter(description = "Details of the file being imported", content = @Content(mediaType = "application/json"))
          @RequestPart(name = "importDetails",
          required = true) @Validated ImportDetails importDetails,
      @Parameter(description = "The file to be imported",content = {@Content(mediaType = "text/tsv"), @Content(mediaType = "text/csv")})
          @RequestPart(name = "file",
          required = true) MultipartFile file) throws HttpMediaTypeNotAcceptableException {

    if (!webSecurity.isValidUser()) {
      throw new NoSuchUserProblem();
    }
    
    return importService.importCodeSet(importDetails, file);
  }

}
