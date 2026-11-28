/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
/* A validation context like the one the page builds (alternatives, TJobs, providers, purchase options). */
export const ctx = {
  langs: ['es', 'en', 'fr', 'de'], themes: ['auto', 'light', 'dark'],
  altIds: ['Azure/westeurope/D2s', 'Azure/westeurope/D4s', 'AWS/eu-west-1/m5.large', 'x,y%z', 'Fargate'],
  nodeIds: ['tjoba', 'tjobb'], providers: ['Azure', 'AWS'], kinds: ['VM', 'CONTAINER'], regions: ['eu-west-1', 'westeurope'],
  cheapestIds: ['Azure/westeurope/D2s', 'Fargate'], buyKeys: ['1-NO_UPFRONT', '3-ALL_UPFRONT'], lang: 'en', defaultLang: 'en'
};
