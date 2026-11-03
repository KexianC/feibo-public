interface AppRequestErrorOptions {
  status?: number;
  code?: string | number;
}

export class AppRequestError extends Error {
  status?: number;
  code?: string | number;

  constructor(message: string, options: AppRequestErrorOptions = {}) {
    super(message);
    this.name = 'AppRequestError';
    this.status = options.status;
    this.code = options.code;
  }
}
