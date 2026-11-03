interface AppLoaderProps {
  message?: string;
}

export default function AppLoader({ message = '正在加载应用...' }: AppLoaderProps) {
  return (
    <div className="app-shell flex h-screen items-center justify-center">
      <div className="app-surface flex min-w-[280px] flex-col items-center rounded-lg px-8 py-7 text-center">
        <div className="relative mb-5 h-12 w-12">
          <div className="absolute inset-0 rounded-full border-4 border-teal-100" />
          <div className="absolute inset-0 animate-spin rounded-full border-4 border-transparent border-t-teal-600" />
        </div>
        <p className="text-sm font-medium text-slate-700">{message}</p>
      </div>
    </div>
  );
}
