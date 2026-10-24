import { createContext, useContext } from "react";

/** Present only inside the demo workspace; `null` everywhere else. */
export type DemoControls = {
  /** Set when the visitor arrived from the portfolio site (see portfolioOrigin.ts). */
  fromPortfolio: boolean;
  /** Throw away the visitor's edits and start from the seed data again. */
  reset: () => void;
  /** Leave the demo (the visitor wants to register or sign in). */
  exit: () => void;
  dirty: boolean;
  readOnlyAccount: boolean;
  /** Set when restoring a saved session failed, so the visitor knows why they see the demo. */
  notice?: string;
  onRetry?: () => void;
};

export const DemoContext = createContext<DemoControls | null>(null);
export const useDemo = (): DemoControls | null => useContext(DemoContext);
