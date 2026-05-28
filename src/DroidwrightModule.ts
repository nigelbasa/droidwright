import { NativeModule, requireNativeModule } from 'expo';

import { DroidwrightModuleEvents } from './Droidwright.types';

declare class DroidwrightModule extends NativeModule<DroidwrightModuleEvents> {
  setValueAsync(value: string): Promise<void>;
}

export default requireNativeModule<DroidwrightModule>('Droidwright');
