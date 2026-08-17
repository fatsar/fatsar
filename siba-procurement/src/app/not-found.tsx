import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <p className="text-4xl font-semibold">404</p>
      <p className="mt-2 text-ink-secondary">
        This record doesn&apos;t exist — it may have been deleted.
      </p>
      <Link
        href="/"
        className="mt-4 inline-block rounded-md border border-accent bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent-strong"
      >
        Back to dashboard
      </Link>
    </div>
  );
}
