interface GeoAdminFrameProps {
  modulePath?: string;
  title?: string;
}

export default function GeoAdminFrame({ modulePath = 'geoWorkbench', title = 'GEOAgent' }: GeoAdminFrameProps) {
  const src = `/geo-agent-app/index.html?embedded=1&v=20260908#/${modulePath}`;

  return (
    <div style={{ height: 'calc(100vh - 104px)', background: '#f5f5f5' }}>
      <iframe
        title={title}
        src={src}
        style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
        allow="clipboard-read; clipboard-write"
      />
    </div>
  );
}
