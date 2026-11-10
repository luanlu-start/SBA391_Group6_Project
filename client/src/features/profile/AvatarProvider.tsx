import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuthentication } from '../../auth/useAuthentication';
import { profileService } from '../../services/profileService';
import { AvatarContext } from './avatarContext';

interface AvatarImage {
  owner: string;
  url: string;
  loading: boolean;
  failed: boolean;
}

export default function AvatarProvider({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuthentication();
  const owner = user?.id;
  const available = !!user?.avatarAvailable;
  const currentOwner = useRef(owner);
  currentOwner.current = owner;
  const pending = useRef<{ owner: string; controller: AbortController; promise: Promise<void> }>();
  const [image, setImage] = useState<AvatarImage>();

  const refresh = useCallback((): Promise<void> => {
    if (!owner || currentOwner.current !== owner) return Promise.resolve();
    if (pending.current?.owner === owner) return pending.current.promise;
    const controller = new AbortController();
    setImage(previous => ({ owner, url: previous?.owner === owner ? previous.url : '', loading: true, failed: false }));
    const promise = profileService.avatar(controller.signal).then(blob => {
      if (controller.signal.aborted || currentOwner.current !== owner) return;
      setImage({ owner, url: URL.createObjectURL(blob), loading: false, failed: false });
    }).catch(() => {
      if (!controller.signal.aborted && currentOwner.current === owner)
        setImage({ owner, url: '', loading: false, failed: true });
    }).finally(() => {
      if (pending.current?.controller === controller) pending.current = undefined;
    });
    pending.current = { owner, controller, promise };
    return promise;
  }, [owner]);

  useEffect(() => {
    if (!owner || !available || initializing) {
      setImage(undefined);
      return;
    }
    void refresh();
    return () => {
      pending.current?.controller.abort();
      pending.current = undefined;
    };
  }, [owner, available, initializing, refresh]);

  const imageUrl = image?.url;
  useEffect(() => () => { if (imageUrl) URL.revokeObjectURL(imageUrl); }, [imageUrl]);

  const current = owner && available && image?.owner === owner ? image : undefined;
  return <AvatarContext.Provider value={{ url: current?.url ?? '', loading: current?.loading ?? false,
    failed: current?.failed ?? false, refresh }}>{children}</AvatarContext.Provider>;
}
