export type DroidwrightModuleEvents = {
  onEvent: (event: DroidwrightEvent) => void;
};

export type DroidwrightEvent = {
  sessionId: string;
  type:
    | 'navigationStarted'
    | 'navigationFinished'
    | 'navigationError'
    | 'console'
    | 'request'
    | 'closed';
  url?: string;
  title?: string;
  method?: string;
  isForMainFrame?: boolean;
  hasGesture?: boolean;
  message?: string;
  level?: string;
  lineNumber?: number;
  sourceId?: string;
  description?: string;
  errorCode?: number;
};

export type DroidwrightLaunchOptions = {
  userAgent?: string;
  debug?: boolean;
  forwardConsole?: boolean;
  loadImages?: boolean;
  viewportWidth?: number;
  viewportHeight?: number;
};

export type DroidwrightActionOptions = {
  timeoutMs?: number;
  visible?: boolean;
  enabled?: boolean;
  stable?: boolean;
  force?: boolean;
  humanPace?: boolean | DroidwrightHumanPaceOptions;
};

export type DroidwrightHumanPaceOptions = {
  enabled?: boolean;
  movementSteps?: number;
  minDelayMs?: number;
  maxDelayMs?: number;
  postActionDelayMs?: number;
};

export type DroidwrightNavigationResult = {
  sessionId: string;
  url: string;
  originalUrl: string;
  title: string;
  progress: number;
  canGoBack: boolean;
  canGoForward: boolean;
  userAgent: string;
};

export type DroidwrightWaitResult = {
  sessionId: string;
  selector: string;
  elapsedMs: number;
};

export type DroidwrightTextWaitResult = {
  sessionId: string;
  text: string;
  exact: boolean;
  elapsedMs: number;
};

export type DroidwrightCookie = {
  name: string;
  value: string;
};

export type DroidwrightCookieResult = {
  accepted?: boolean;
  removed?: boolean;
};

export type DroidwrightScrollResult = {
  ok: boolean;
  scrollX?: number;
  scrollY?: number;
};

export type DroidwrightTextLocatorOptions = DroidwrightActionOptions & {
  exact?: boolean;
};

export type DroidwrightStorageArea = 'localStorage' | 'sessionStorage';
