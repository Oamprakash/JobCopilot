"use client";

import { useEffect } from "react";
import { captureAcquisition } from "@/lib/acquisition";

/** Notes where this browser's first visit came from (see lib/acquisition). Renders nothing. */
export function AcquisitionCapture() {
  useEffect(() => {
    captureAcquisition();
  }, []);
  return null;
}
