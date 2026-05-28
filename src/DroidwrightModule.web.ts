import { registerWebModule, NativeModule } from 'expo';

import { DroidwrightModuleEvents } from './Droidwright.types';

// DroidwrightModule is not available on the web platform.
class DroidwrightModule extends NativeModule<DroidwrightModuleEvents> {}

export default registerWebModule(DroidwrightModule, 'DroidwrightModule');
