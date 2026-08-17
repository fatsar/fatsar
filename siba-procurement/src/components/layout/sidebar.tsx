"use client";

import { cn } from "@/lib/cn";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS } from "./nav-items";

export function isNavActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav className="flex flex-col gap-0.5 px-2" aria-label="Main navigation">
      {NAV_ITEMS.map((item) => {
        const active = isNavActive(pathname, item.href);
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-[13.5px] font-medium transition-colors",
              active
                ? "bg-nav-active text-nav-text-active"
                : "text-nav-text hover:bg-nav-hover hover:text-nav-text-active"
            )}
          >
            <Icon size={16} strokeWidth={2} className="shrink-0" />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}

export function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col bg-nav lg:flex">
      <Link href="/" className="flex items-center gap-2.5 px-4 pb-4 pt-5">
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-accent text-[15px] font-bold text-white">
          S
        </span>
        <span className="leading-tight">
          <span className="block text-[14px] font-semibold text-white">
            Siba Procurement
          </span>
          <span className="block text-[11px] tracking-wide text-nav-text">
            COMMAND CENTER
          </span>
        </span>
      </Link>
      <div className="flex-1 overflow-y-auto pb-4">
        <NavLinks />
      </div>
      <div className="border-t border-white/10 px-4 py-3 text-[11px] leading-4 text-nav-text">
        Internal procurement intelligence.
        <br />
        Confidential — Siba Kimya.
      </div>
    </aside>
  );
}
