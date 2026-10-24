/**
 * Kept apart from api.ts so the in-browser demo can raise the same error type
 * without importing the module that talks to the network.
 */
export class ApiError extends Error {
  code: string;
  status: number;
  fields?: Record<string, string>;
  constructor(
    message: string,
    status = 0,
    code = "NETWORK_ERROR",
    fields?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}
