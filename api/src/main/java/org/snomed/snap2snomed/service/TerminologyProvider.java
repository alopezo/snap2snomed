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

package org.snomed.snap2snomed.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.snomed.snap2snomed.config.Snap2snomedConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TerminologyProvider {

    @Autowired
    Snap2snomedConfiguration configuration;

    private FhirContext fhirContext;
    private TerminologyClient client;
    private IGenericClient genericClient;
    private volatile TerminologyServerKind serverKind;

    public FhirContext getFhirContext() {
        if (this.fhirContext == null) {
            this.fhirContext = FhirContext.forR4();
        }
        return this.fhirContext;
    }

    public IGenericClient getGenericClient() {
        if (this.genericClient == null) {
            this.genericClient = getFhirContext().newRestfulGenericClient(configuration.getDefaultTerminologyServer().getUrl());
        }
        return this.genericClient;
    }

    /**
     * Detects the terminology server implementation from its CapabilityStatement. The result is
     * cached; if the metadata cannot be read, OTHER is returned and detection is retried next time.
     */
    public TerminologyServerKind getServerKind() {
        if (this.serverKind == null) {
            try {
                final CapabilityStatement capabilities = getGenericClient().capabilities()
                        .ofType(CapabilityStatement.class).execute();
                this.serverKind = TerminologyServerKind.fromSoftwareName(
                        capabilities.hasSoftware() ? capabilities.getSoftware().getName() : null);
                log.info("Terminology server detected as {}", this.serverKind);
            } catch (final Exception e) {
                log.warn("Could not read the terminology server CapabilityStatement: {}", e.getMessage());
                return TerminologyServerKind.OTHER;
            }
        }
        return this.serverKind;
    }

    public TerminologyClient getClient() {
        if (this.client == null) {
            IRestfulClientFactory restfulClientFactory = getFhirContext().getRestfulClientFactory();
            this.client = restfulClientFactory.newClient(TerminologyClient.class,
                    configuration.getDefaultTerminologyServer().getUrl());
        }
        return this.client;
    }
}
