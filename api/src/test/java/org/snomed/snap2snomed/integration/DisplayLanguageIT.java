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

package org.snomed.snap2snomed.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.snomed.snap2snomed.controller.dto.TargetDisplayRefreshResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Target display resolution against the configured terminology server (the public Ontoserver, as
 * in ValidateCodesIT): imported displays are replaced by the term in the map's display language,
 * and the refresh endpoint re-resolves them on demand.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DisplayLanguageIT extends IntegrationTestBase {

  private static final String AU_VERSION = "http://snomed.info/sct/32506021000036107/version/20210731";
  private static final String MEMBER_TEST_USER = "display-language-member";
  private static final String E_COLI = "112283007";
  // Valid SCTID (check digit included) that is not a concept, so the terminology server does not return it
  private static final String UNRESOLVED_CODE = "9999999007";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long projectId;
  private Long codeSetId;
  private File mappingFile;

  @Override
  protected void beforeTests() throws IOException {
    restClient.createOrUpdateUser(DEFAULT_TEST_USER_SUBJECT, "Test", "Bobby", "User", "test@user.com");
    restClient.createOrUpdateUser(MEMBER_TEST_USER, "Member", "Mia", "Member", "member@user.com");
    projectId = restClient.createProject("Display language project", "Display language tests",
        Set.of(DEFAULT_TEST_USER_SUBJECT), Set.of(MEMBER_TEST_USER), Set.of());
    codeSetId = restClient.createImportedCodeSet("Display language codes", "1.0", 0, 1, true, ",",
        new ClassPathResource("Pathology Organisms 202103.csv").getFile(), "text/csv");
    mappingFile = File.createTempFile("s2s-display-language", ".csv");
    Files.writeString(mappingFile.toPath(), String.join("\n",
        "Source code,Target code,Target display,Relationship",
        "ESCCOL," + E_COLI + ",Imported display,TARGET_INEXACT",
        "AERCAV," + UNRESOLVED_CODE + ",Kept display,TARGET_INEXACT",
        ""));
  }

  private Long importMapping(String mapVersion) throws IOException {
    final Long mapId = restClient.createMap(mapVersion, AU_VERSION, "*", projectId, codeSetId);
    restClient.createImportedMap(0, 1, 2, 3, -1, -1, true, ",", mappingFile, "text/csv", mapId);
    return mapId;
  }

  private String targetDisplay(Long mapId, String code) {
    final List<String> displays = jdbcTemplate.queryForList(
        "select t.target_display from map_row_target t join map_row r on t.row_id = r.id where r.map_id = ? and t.target_code = ?",
        String.class, mapId, code);
    assertThat(displays).hasSize(1);
    return displays.get(0);
  }

  @Test
  void importReplacesDisplaysWithTheEnglishFsnWhenTheMapHasNoDisplayLanguage() throws IOException {
    final Long mapId = importMapping("import-no-language");

    assertThat(targetDisplay(mapId, E_COLI)).isEqualTo("Escherichia coli (organism)");
    assertThat(targetDisplay(mapId, UNRESOLVED_CODE)).isEqualTo("Kept display");
  }

  @Test
  void refreshUsesTheMapDisplayLanguage() throws IOException {
    final Long mapId = importMapping("refresh-language");
    jdbcTemplate.update("update map set display_language = ? where id = ?", "en-X-900000000000509007", mapId);

    final TargetDisplayRefreshResult result = restClient.refreshTargetDisplays(DEFAULT_TEST_USER_SUBJECT, mapId)
        .statusCode(is(200)).extract().as(TargetDisplayRefreshResult.class);

    assertThat(result).isEqualTo(new TargetDisplayRefreshResult(2, 1, 1));
    assertThat(targetDisplay(mapId, E_COLI)).isEqualTo("Escherichia coli");
    assertThat(targetDisplay(mapId, UNRESOLVED_CODE)).isEqualTo("Kept display");

    // Nothing left to change on a second run
    assertThat(restClient.refreshTargetDisplays(DEFAULT_TEST_USER_SUBJECT, mapId)
        .statusCode(is(200)).extract().as(TargetDisplayRefreshResult.class).getUpdatedCount()).isZero();
  }

  @Test
  void onlyOwnersCanRefresh() throws IOException {
    final Long mapId = importMapping("refresh-member");

    restClient.refreshTargetDisplays(MEMBER_TEST_USER, mapId).statusCode(is(403));
  }
}
