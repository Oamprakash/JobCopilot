"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
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
