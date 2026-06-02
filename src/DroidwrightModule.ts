import { NativeModule, requireNativeModule } from 'expo';

import {
  DroidwrightModuleEvents,
  DroidwrightCookie,
  DroidwrightCookieResult,
  DroidwrightNavigationResult,
  DroidwrightScrollResult,
  DroidwrightTextWaitResult,
  DroidwrightWaitResult,
} from './Droidwright.types';

declare class DroidwrightModule extends NativeModule<DroidwrightModuleEvents> {
  createSession(optionsJson: string): Promise<DroidwrightNavigationResult>;
  closeSession(sessionId: string): Promise<void>;
  closeAllSessions(): Promise<void>;
  goto(sessionId: string, url: string, timeoutMs: number): Promise<DroidwrightNavigationResult>;
  reload(sessionId: string, timeoutMs: number): Promise<DroidwrightNavigationResult>;
  goBack(sessionId: string, timeoutMs: number): Promise<DroidwrightNavigationResult>;
  evaluate(sessionId: string, script: string): Promise<string | null>;
  waitForSelector(
    sessionId: string,
    selector: string,
    timeoutMs: number
  ): Promise<DroidwrightWaitResult>;
  click(
    sessionId: string,
    selector: string,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  tap(
    sessionId: string,
    selector: string,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  fill(
    sessionId: string,
    selector: string,
    value: string,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  textContent(sessionId: string, selector: string, timeoutMs: number): Promise<string>;
  waitForText(
    sessionId: string,
    text: string,
    exact: boolean,
    timeoutMs: number
  ): Promise<DroidwrightTextWaitResult>;
  clickText(
    sessionId: string,
    text: string,
    exact: boolean,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  textContentByText(
    sessionId: string,
    text: string,
    exact: boolean,
    timeoutMs: number
  ): Promise<string>;
  scrollBy(sessionId: string, x: number, y: number): Promise<DroidwrightScrollResult>;
  scrollTo(sessionId: string, x: number, y: number): Promise<DroidwrightScrollResult>;
  scrollIntoView(
    sessionId: string,
    selector: string,
    timeoutMs: number
  ): Promise<DroidwrightScrollResult>;
  getCookies(url: string): Promise<DroidwrightCookie[]>;
  setCookie(url: string, cookie: string): Promise<DroidwrightCookieResult>;
  clearCookies(): Promise<DroidwrightCookieResult>;
  snapshot(sessionId: string): Promise<DroidwrightNavigationResult>;
  screenshot(sessionId: string): Promise<string>;
  hover(
    sessionId: string,
    selector: string,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  press(
    sessionId: string,
    selector: string,
    key: string,
    timeoutMs: number,
    actionOptionsJson: string
  ): Promise<Record<string, unknown>>;
  selectOption(
    sessionId: string,
    selector: string,
    value: string,
    timeoutMs: number
  ): Promise<Record<string, unknown>>;
}

export default requireNativeModule<DroidwrightModule>('Droidwright');
