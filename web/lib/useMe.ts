"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { acquisitionSentFor, markAcquisitionSent, storedAcquisition } from "./acquisition";
import { api, ApiError, type Me } from "./api";

/** Loads the signed-in user and sends anyone without a session to /login. */
export function useMe(): Me | null {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .me()
      .then((user) => {
        if (!cancelled) setMe(user);
        // Once per user and browser: where they first came from. The server keeps it only for new accounts.
        const acquisition = storedAcquisition();
        if (acquisition && !acquisitionSentFor(user.id)) {
          api.reportAcquisition(acquisition).then(() => markAcquisitionSent(user.id)).catch(() => undefined);
        }
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) router.replace("/login");
      });
    return () => {
      cancelled = true;
    };
  }, [router]);

  return me;
}
