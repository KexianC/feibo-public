interface EditorLoadingFallbackProps {
  label?: string;
  className?: string;
}

export default function EditorLoadingFallback({
  label = '正在加载模块...',
  className = 'h-full',
}: EditorLoadingFallbackProps) {
  return (
    <div className={`${className} flex items-center justify-center bg-slate-50 text-slate-500 text-sm`}>
      <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 shadow-sm">
        <span className="h-2 w-2 animate-pulse rounded-full bg-teal-500" />
        <span>{label}</span>
      </div>
    </div>
  );
}
