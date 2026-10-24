import { createContext, useContext } from "react";
import { api, type Api } from "./api";

/**
 * Components talk to the server through this context instead of importing `api`
 * directly. The demo workspace provides an in-memory implementation, so the
 * binding is decided by where a component is rendered, not by a global switch.
 * That also means a request started inside the demo can never fall through to
 * the real server after the visitor signs in.
 */
export const ApiContext = createContext<Api>(api);
export const useApi = (): Api => useContext(ApiContext);
