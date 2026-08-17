import type { MetadataRoute } from "next";

/**
 * Basic web app manifest so the tool is installable (PWA groundwork).
 * A service worker / offline support is deliberately out of Phase 1 scope.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Siba Procurement Command Center",
    short_name: "Siba Procurement",
    description:
      "Internal procurement intelligence: purchasing decisions, supplier intelligence, price history, stock coverage and market risk.",
    start_url: "/",
    display: "standalone",
    background_color: "#f3f3f1",
    theme_color: "#141d2e",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }],
  };
}
