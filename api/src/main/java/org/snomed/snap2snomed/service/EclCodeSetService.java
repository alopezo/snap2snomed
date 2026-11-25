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

package org.snomed.snap2snomed.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snap2snomed.controller.dto.EclCodeSetRequest;
import org.snomed.snap2snomed.model.ImportedCodeSet;
import org.snomed.snap2snomed.problem.CodeSetImportProblem;
import org.snomed.snap2snomed.util.LanguageDialects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the source code set of a map from SNOMED CT: the active concepts of an ECL expression in a
 * SNOMED CT version, with their terms in the map's display language, frozen as an imported code set.
 */
@Service
@Slf4j
public class EclCodeSetService {

  /** Largest ECL result accepted as the source of a map from SNOMED CT. */
  public static final int MAX_CONCEPTS = 10000;

  static final String SNOMED_SYSTEM = FhirService.DEFAULT_CODE_SYSTEM;
  private static final int PAGE_SIZE = 1000;
  private static final Pattern EFFECTIVE_TIME = Pattern.compile("/version/([0-9]{8})$");

  @Autowired
  TerminologyProvider terminologyProvider;

  @Autowired
  CodeSetImportService codeSetImportService;

  /**
   * Expands the ECL and stores the result as a new imported code set.
   *
   * @throws CodeSetImportProblem when the ECL returns no concepts or more than {@link #MAX_CONCEPTS}
   */
  public ImportedCodeSet createCodeSet(EclCodeSetRequest request) {
    final String valueSetUrl = request.getVersion() + "?fhir_vs=ecl/" + request.getEcl().trim();
    final boolean englishFsn = LanguageDialects.normalize(request.getDisplayLanguage()) == null;
    final String acceptLanguage = englishFsn
        ? "en"
        : LanguageDialects.toServerFormat(request.getDisplayLanguage(), terminologyProvider.getServerKind());

    final ValueSet first = expand(valueSetUrl, 0, 1, false, acceptLanguage);
    final int total = first.getExpansion().getTotal();
    if (total == 0) {
      throw new CodeSetImportProblem("ecl-empty", "The ECL expression returns no concepts",
          "The ECL expression returns no active concepts in " + request.getVersion());
    }
    if (total > MAX_CONCEPTS) {
      throw new CodeSetImportProblem("ecl-too-large", "The ECL expression returns too many concepts",
          "The ECL expression returns " + total + " concepts; a map from SNOMED CT accepts at most " + MAX_CONCEPTS);
    }

    // Code to term, in the order the server returns them
    final Map<String, String> concepts = new LinkedHashMap<>();
    for (int offset = 0; offset < total; offset += PAGE_SIZE) {
      final ValueSet page = expand(valueSetUrl, offset, PAGE_SIZE, englishFsn, acceptLanguage);
      for (final ValueSet.ValueSetExpansionContainsComponent concept : page.getExpansion().getContains()) {
        final String display = englishFsn ? TargetDisplayResolver.englishFsn(concept) : concept.getDisplay();
        concepts.put(concept.getCode(), display == null || display.isBlank() ? concept.getDisplay() : display);
      }
      if (page.getExpansion().getContains().isEmpty()) {
        break;
      }
    }

    final ImportedCodeSet codeSet = ImportedCodeSet.builder()
        .name(request.getName())
        .version(effectiveTime(request.getVersion()))
        .systemUri(SNOMED_SYSTEM)
        .valuesetUri(valueSetUrl.length() <= 255 ? valueSetUrl : null)
        .additionalColumnsMetadata(new ArrayList<>())
        .build();
    log.info("Creating SNOMED CT source code set '{}' with {} concepts from ECL {}", request.getName(), concepts.size(),
        request.getEcl());
    return codeSetImportService.saveCodeSet(codeSet, new ArrayList<>(concepts.entrySet()));
  }

  /** An ECL expansion: its total and, unless it was over the limit asked for, code to term. */
  public record Expansion(int total, Map<String, String> concepts) {
    public boolean tooLarge() {
      return concepts == null;
    }
  }

  /**
   * The active concepts an ECL expression returns in a SNOMED CT version, with their terms in the display
   * language (English FSN when none). Over {@code max} concepts, only the total is returned.
   */
  public Expansion expandConcepts(String version, String ecl, String displayLanguage, int max) {
    final String valueSetUrl = version + "?fhir_vs=ecl/" + ecl.trim();
    final boolean englishFsn = LanguageDialects.normalize(displayLanguage) == null;
    final String acceptLanguage = englishFsn
        ? "en"
        : LanguageDialects.toServerFormat(displayLanguage, terminologyProvider.getServerKind());
    final int total = expand(valueSetUrl, 0, 1, false, acceptLanguage).getExpansion().getTotal();
    if (total > max) {
      return new Expansion(total, null);
    }
    final Map<String, String> concepts = new LinkedHashMap<>();
    for (int offset = 0; offset < total; offset += PAGE_SIZE) {
      final ValueSet page = expand(valueSetUrl, offset, PAGE_SIZE, englishFsn, acceptLanguage);
      for (final ValueSet.ValueSetExpansionContainsComponent concept : page.getExpansion().getContains()) {
        final String display = englishFsn ? TargetDisplayResolver.englishFsn(concept) : concept.getDisplay();
        concepts.put(concept.getCode(), display == null || display.isBlank() ? concept.getDisplay() : display);
      }
      if (page.getExpansion().getContains().isEmpty()) {
        break;
      }
    }
    return new Expansion(total, concepts);
  }

  /** The codes of the active concepts an ECL expression returns in a SNOMED CT version (no terms). */
  public List<String> expandCodes(String version, String ecl) {
    final String valueSetUrl = version + "?fhir_vs=ecl/" + ecl.trim();
    final List<String> codes = new ArrayList<>();
    int total = Integer.MAX_VALUE;
    for (int offset = 0; offset < total; offset += PAGE_SIZE) {
      final ValueSet page = expand(valueSetUrl, offset, PAGE_SIZE, false, "en");
      total = page.getExpansion().getTotal();
      page.getExpansion().getContains().forEach(concept -> codes.add(concept.getCode()));
      if (page.getExpansion().getContains().isEmpty()) {
        break;
      }
    }
    return codes;
  }

  /**
   * Like {@link #expandCodes}, for an ECL expression too long for a URL (e.g. thousands of codes joined by OR):
   * a POST $expand of a value set given inline (SNOMED CT with an ECL "constraint" filter).
   */
  public List<String> expandCodesInline(String version, String ecl) {
    final List<String> codes = new ArrayList<>();
    int total = Integer.MAX_VALUE;
    for (int offset = 0; offset < total; offset += PAGE_SIZE) {
      final ValueSet valueSet = new ValueSet();
      valueSet.getCompose().addInclude().setSystem(SNOMED_SYSTEM).setVersion(version).addFilter()
          .setProperty("constraint").setOp(ValueSet.FilterOperator.EQUAL).setValue(ecl.trim());
      final Parameters parameters = new Parameters();
      parameters.addParameter().setName("valueSet").setResource(valueSet);
      parameters.addParameter("offset", new IntegerType(offset));
      parameters.addParameter("count", new IntegerType(PAGE_SIZE));
      parameters.addParameter("activeOnly", new BooleanType(true));
      final ValueSet page;
      try {
        page = terminologyProvider.getGenericClient().operation()
            .onType(ValueSet.class)
            .named("$expand")
            .withParameters(parameters)
            .returnResourceType(ValueSet.class)
            .execute();
      } catch (final BaseServerResponseException e) {
        log.warn("Inline ECL expansion failed ({} characters): {}", ecl.length(), e.getMessage());
        throw new CodeSetImportProblem("ecl-expansion-failed", "The terminology server could not expand the ECL expression",
            e.getMessage());
      }
      total = page.getExpansion().getTotal();
      page.getExpansion().getContains().forEach(concept -> codes.add(concept.getCode()));
      if (page.getExpansion().getContains().isEmpty()) {
        break;
      }
    }
    return codes;
  }

  private ValueSet expand(String valueSetUrl, int offset, int count, boolean includeDesignations, String acceptLanguage) {
    final Parameters parameters = new Parameters();
    parameters.addParameter("url", new UriType(valueSetUrl));
    parameters.addParameter("offset", new IntegerType(offset));
    parameters.addParameter("count", new IntegerType(count));
    parameters.addParameter("activeOnly", new BooleanType(true));
    parameters.addParameter("includeDesignations", new BooleanType(includeDesignations));
    try {
      // GET, like the user interface: Snowstorm does not read the url parameter of a POST $expand
      return terminologyProvider.getGenericClient().operation()
          .onType(ValueSet.class)
          .named("$expand")
          .withParameters(parameters)
          .useHttpGet()
          .returnResourceType(ValueSet.class)
          .withAdditionalHeader("Accept-Language", acceptLanguage)
          .execute();
    } catch (final BaseServerResponseException e) {
      log.warn("ECL expansion failed for {}: {}", valueSetUrl, e.getMessage());
      throw new CodeSetImportProblem("ecl-expansion-failed", "The terminology server could not expand the ECL expression",
          e.getMessage());
    }
  }

  /** The code set version: the effective time of the SNOMED CT version (code set versions are short). */
  static String effectiveTime(String versionUri) {
    final Matcher matcher = EFFECTIVE_TIME.matcher(versionUri);
    return matcher.find() ? matcher.group(1) : versionUri.substring(Math.max(0, versionUri.length() - 30));
  }
}
