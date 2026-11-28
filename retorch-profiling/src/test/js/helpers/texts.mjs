/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* The translations of the page: resources/usageprofile/i18n/<lang>.json. */
import fs from 'node:fs';
import path from 'node:path';
import {JS_DIR} from './load.mjs';

export const RESOURCES = path.resolve(JS_DIR, '..');
export const LANGS = ['es', 'en', 'fr', 'de'];
export const texts = Object.fromEntries(LANGS.map((l) => [l, JSON.parse(fs.readFileSync(path.join(RESOURCES, 'i18n', l + '.json'), 'utf8'))]));
