"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";

const items = [
  { href: "/", label: "Start", icon: "◉" },
  { href: "/eq/", label: "EQ", icon: "≣" },
  { href: "/settings/", label: "Setup", icon: "⚙" },
  { href: "/info/", label: "Info", icon: "ℹ" },
];

export default function Nav() {
  const path = usePathname();
  return (
    <nav className="fixed bottom-0 inset-x-0 bg-panel/95 backdrop-blur border-t border-line">
      <div className="max-w-md mx-auto grid grid-cols-4">
        {items.map((it) => {
          const active = path === it.href || (it.href !== "/" && path.startsWith(it.href));
          return (
            <Link key={it.href} href={it.href}
              className={`py-3 text-center text-xs ${active ? "text-accent" : "text-gray-500"}`}>
              <div className="text-lg leading-none">{it.icon}</div>
              {it.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
