import React, { useEffect, useMemo, useState } from 'react';
import { User as UserIcon } from 'lucide-react';
import { sanitizeAvatarUrl } from '../utils/avatar';

const loadedAvatarUrls = new Set<string>();
const avatarImageCache = new Map<string, HTMLImageElement>();

interface CachedAvatarProps {
  src?: string | null;
  alt?: string;
  className?: string;
  fallbackClassName?: string;
  iconClassName?: string;
  fallbackSrc?: string;
  loading?: 'eager' | 'lazy';
}

const CachedAvatar: React.FC<CachedAvatarProps> = ({
  src,
  alt = '头像',
  className = 'h-full w-full object-cover',
  fallbackClassName = 'flex h-full w-full items-center justify-center bg-gray-200',
  iconClassName = 'h-6 w-6 text-gray-400',
  fallbackSrc,
  loading = 'eager'
}) => {
  const primarySrc = useMemo(() => sanitizeAvatarUrl(src), [src]);
  const fallbackUrl = useMemo(() => sanitizeAvatarUrl(fallbackSrc), [fallbackSrc]);
  const initialSrc = primarySrc || fallbackUrl;
  const [effectiveSrc, setEffectiveSrc] = useState(initialSrc);
  const [failed, setFailed] = useState(!initialSrc);
  const [isLoaded, setIsLoaded] = useState(() => !!initialSrc && loadedAvatarUrls.has(initialSrc));

  useEffect(() => {
    const nextSrc = primarySrc || fallbackUrl;
    setEffectiveSrc(nextSrc);
    setFailed(!nextSrc);
    setIsLoaded(!!nextSrc && loadedAvatarUrls.has(nextSrc));
  }, [fallbackUrl, primarySrc]);

  useEffect(() => {
    if (!effectiveSrc || loadedAvatarUrls.has(effectiveSrc) || typeof Image === 'undefined') return;

    let cachedImage = avatarImageCache.get(effectiveSrc);
    if (!cachedImage) {
      cachedImage = new Image();
      cachedImage.decoding = 'async';
      cachedImage.loading = 'eager';
      cachedImage.src = effectiveSrc;
      avatarImageCache.set(effectiveSrc, cachedImage);
    }

    if (cachedImage.complete && cachedImage.naturalWidth > 0) {
      loadedAvatarUrls.add(effectiveSrc);
      setIsLoaded(true);
      return;
    }

    const handleLoad = () => {
      loadedAvatarUrls.add(effectiveSrc);
      setIsLoaded(true);
    };
    cachedImage.addEventListener('load', handleLoad, { once: true });

    return () => cachedImage.removeEventListener('load', handleLoad);
  }, [effectiveSrc]);

  if (!effectiveSrc || failed) {
    return (
      <div className={fallbackClassName}>
        <UserIcon className={iconClassName} />
      </div>
    );
  }

  return (
    <img
      src={effectiveSrc}
      alt={alt}
      className={className}
      loading={loading}
      decoding="async"
      onLoad={() => {
        loadedAvatarUrls.add(effectiveSrc);
        setIsLoaded(true);
      }}
      onError={() => {
        if (fallbackUrl && effectiveSrc !== fallbackUrl) {
          setEffectiveSrc(fallbackUrl);
          return;
        }
        setFailed(true);
      }}
      data-avatar-loaded={isLoaded ? 'true' : 'false'}
    />
  );
};

export default CachedAvatar;
