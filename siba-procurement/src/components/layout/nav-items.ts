import {
  CalendarClock,
  ChartLine,
  Factory,
  FileText,
  FlaskConical,
  Globe2,
  LayoutDashboard,
  ListTodo,
  Settings,
  Upload,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/materials", label: "Materials", icon: FlaskConical },
  { href: "/suppliers", label: "Suppliers", icon: Factory },
  { href: "/quotations", label: "Quotations", icon: FileText },
  { href: "/plan", label: "Purchasing Plan", icon: CalendarClock },
  { href: "/price-history", label: "Price History", icon: ChartLine },
  { href: "/market-intelligence", label: "Market Intelligence", icon: Globe2 },
  { href: "/actions", label: "Action Center", icon: ListTodo },
  { href: "/imports", label: "Files / Imports", icon: Upload },
  { href: "/settings", label: "Settings", icon: Settings },
];
