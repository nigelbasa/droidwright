// Reexport the native module. On web, it will be resolved to DroidwrightModule.web.ts
// and on native platforms to DroidwrightModule.ts
export { default } from './DroidwrightModule';
export * from './Droidwright.types';
