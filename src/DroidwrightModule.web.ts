import { NativeModule, registerWebModule } from 'expo';

import { DroidwrightModuleEvents } from './Droidwright.types';

class DroidwrightModule extends NativeModule<DroidwrightModuleEvents> {
  createSession(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  closeSession(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  closeAllSessions(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  goto(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  reload(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  goBack(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  evaluate(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  waitForSelector(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  click(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  tap(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  fill(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  textContent(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  waitForText(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  clickText(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  textContentByText(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  scrollBy(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  scrollTo(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  scrollIntoView(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  getCookies(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  setCookie(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  clearCookies(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  snapshot(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  screenshot(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  hover(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  press(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }

  selectOption(): Promise<never> {
    return Promise.reject(createUnsupportedError());
  }
}

function createUnsupportedError() {
  return new Error('Droidwright runs on Android native WebView, not on web.');
}

export default registerWebModule(DroidwrightModule, 'DroidwrightModule');
