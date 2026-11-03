export const ROUTE_PATHS = {
  root: '/',
  login: '/login',
  zhibi: '/zhibi',
  zhihe: '/zhihe',
  synthesis: '/synthesis',
  videoGeneration: '/video-generation',
  account: '/account',
  publish: '/publish',
} as const;

export type AppRoutePath = (typeof ROUTE_PATHS)[keyof typeof ROUTE_PATHS];
