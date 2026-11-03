import { useEffect, useState } from 'react';

const BASE_WIDTH = 1920;
const BASE_HEIGHT = 953;

function getViewportSize() {
  const viewport = window.visualViewport;

  return {
    width: viewport?.width ?? window.innerWidth,
    height: viewport?.height ?? window.innerHeight,
  };
}

function getUniformScale() {
  const viewport = getViewportSize();
  const scale = Math.min(viewport.width / BASE_WIDTH, viewport.height / BASE_HEIGHT);

  return {
    x: scale,
    y: scale,
  };
}

export function useViewportScale() {
  const [scale, setScale] = useState(() => getUniformScale());

  useEffect(() => {
    let frameId = 0;

    const updateScale = () => {
      window.cancelAnimationFrame(frameId);
      frameId = window.requestAnimationFrame(() => {
        setScale(getUniformScale());
      });
    };

    updateScale();
    window.addEventListener('resize', updateScale);
    window.visualViewport?.addEventListener('resize', updateScale);

    return () => {
      window.cancelAnimationFrame(frameId);
      window.removeEventListener('resize', updateScale);
      window.visualViewport?.removeEventListener('resize', updateScale);
    };
  }, []);

  return scale;
}

export const VIEWPORT_BASE_SIZE = {
  width: BASE_WIDTH,
  height: BASE_HEIGHT,
} as const;
