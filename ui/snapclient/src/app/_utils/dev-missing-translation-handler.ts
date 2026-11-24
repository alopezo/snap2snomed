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

import {isDevMode} from '@angular/core';
import {MissingTranslationHandler, MissingTranslationHandlerParams} from '@ngx-translate/core';

/**
 * Called when a key is missing from both the current language and English (the fallback):
 * warns in development builds and shows the key, as ngx-translate does by default.
 */
export class DevMissingTranslationHandler implements MissingTranslationHandler {
  handle(params: MissingTranslationHandlerParams): string {
    if (isDevMode()) {
      console.warn('Missing translation key: ' + params.key);
    }
    return params.key;
  }
}
