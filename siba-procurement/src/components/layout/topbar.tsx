"use client";

import { Menu, X } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { GlobalSearch } from "./global-search";
import { NavLinks } from "./sidebar";

export function TopBar() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <>
      <header className="sticky top-0 z-20 border-b border-line bg-surface/95 backdrop-blur">
        <div className="flex h-13 items-center gap-3 px-4 py-2 lg:px-6">
          <button
            type="button"
            className="rounded-md border border-line-strong p-1.5 text-ink-secondary lg:hidden"
            onClick={() => setMobileNavOpen(true)}
            aria-label="Open navigation"
          >
            <Menu size={18} />
          </button>
          <Link href="/" className="font-semibold lg:hidden">
            Siba Procurement
          </Link>
          <GlobalSearch className="ml-auto w-full max-w-xl" />
        </div>
      </header>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="absolute inset-0 bg-ink/50"
            onClick={() => setMobileNavOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 flex w-72 flex-col bg-nav">
            <div className="flex items-center justify-between px-4 pb-3 pt-4">
              <span className="text-[14px] font-semibold text-white">
                Siba Procurement
              </span>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                aria-label="Close navigation"
                className="rounded p-1 text-nav-text hover:text-white"
              >
                <X size={18} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto pb-4">
              <NavLinks onNavigate={() => setMobileNavOpen(false)} />
            </div>
          </div>
        </div>
      )}
    </>
  );
}
