import axios, { isAxiosError, type InternalAxiosRequestConfig } from "axios";

export const LOGIN_PATH = "/auth/login";

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let tokenProvider: TokenProvider = () => null;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export const apiClient = axios.create({
  baseURL: "/api",
  timeout: 10000,
  headers: { Accept: "application/json" },
});

/** Registers where the client reads the access token from. */
export function setAuthTokenProvider(provider: TokenProvider) {
  tokenProvider = provider;
}

/** Registers what to do when an authenticated call answers 401. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

function isLoginRequest(config: InternalAxiosRequestConfig | undefined) {
  return config?.url?.endsWith(LOGIN_PATH) ?? false;
}

apiClient.interceptors.request.use((config) => {
  const token = tokenProvider();
  if (token && !config.headers.has("Authorization")) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      isAxiosError(error) &&
      error.response?.status === 401 &&
      !isLoginRequest(error.config)
    ) {
      unauthorizedHandler?.();
    }
    return Promise.reject(error);
  },
);
